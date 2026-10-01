package com.annaschneider.minecraft1.link;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Desktop side of the link. {@link #connect} performs the {@code hello} handshake; {@link #send} returns a future that
 * completes with the matching response (or fails with {@link LinkException} on timeout/disconnect). Progress and log
 * messages are delivered to the {@link Listener} on the reader thread.
 */
public final class LinkClient implements AutoCloseable {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final Socket socket;
    private final OutputStream out;
    private final Listener listener;
    private final Duration timeout;
    private final Map<String, CompletableFuture<LinkMessage>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile ServerInfo serverInfo;

    public interface Listener {
        /** Progress or log message pushed by the mod. */
        void onEvent(LinkMessage message);

        /** The connection was closed by either side; {@code reason} is user-facing. */
        void onDisconnected(String reason);
    }

    private LinkClient(Socket socket, Listener listener, Duration timeout) throws IOException {
        this.socket = socket;
        this.out = socket.getOutputStream();
        this.listener = Objects.requireNonNull(listener, "listener");
        this.timeout = timeout;
    }

    /**
     * Connects to the mod and authenticates.
     *
     * @throws LinkException with a user-facing explanation (Minecraft not running, wrong token, ...)
     */
    public static LinkClient connect(LinkInfo info, String clientName, String player, Listener listener, Duration timeout)
        throws LinkException {
        String host = info.host() == null || info.host().isBlank() ? "127.0.0.1" : info.host();
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, info.port()), (int) Math.min(Integer.MAX_VALUE, timeout.toMillis()));
        } catch (ConnectException ex) {
            closeQuietly(socket);
            throw new LinkException("Minecraft is not reachable on " + host + ":" + info.port() + ". Start Minecraft with "
                + "the Architect mod, open a world, then click Connect.");
        } catch (SocketTimeoutException ex) {
            closeQuietly(socket);
            throw new LinkException("Timed out connecting to Minecraft on " + host + ":" + info.port() + ".");
        } catch (IOException ex) {
            closeQuietly(socket);
            throw new LinkException("Could not connect to Minecraft on " + host + ":" + info.port() + ": " + ex.getMessage());
        }
        LinkClient client;
        try {
            client = new LinkClient(socket, listener, timeout);
        } catch (IOException ex) {
            closeQuietly(socket);
            throw new LinkException("Could not open the connection: " + ex.getMessage());
        }
        Thread reader = new Thread(client::readLoop, "architect-link-client");
        reader.setDaemon(true);
        reader.start();
        LinkMessage welcome = client.call(LinkRequest.hello(info.token(), clientName, player));
        if (!welcome.isOk()) {
            client.close();
            throw new LinkException(welcome.message());
        }
        client.serverInfo = welcome.server();
        return client;
    }

    public ServerInfo serverInfo() {
        return serverInfo;
    }

    public boolean isConnected() {
        return open.get();
    }

    /** Validates and sends a request; the future fails with {@link LinkException}. */
    public CompletableFuture<LinkMessage> send(LinkRequest request) {
        LinkRequest withId = request.withId(Long.toString(nextId.getAndIncrement()));
        CompletableFuture<LinkMessage> future = new CompletableFuture<>();
        try {
            RequestValidator.validate(withId);
        } catch (LinkProtocolException ex) {
            future.completeExceptionally(new LinkException(ex.getMessage()));
            return future;
        }
        if (!open.get()) {
            future.completeExceptionally(new LinkException("Not connected to Minecraft."));
            return future;
        }
        inFlight.put(withId.id(), future);
        try {
            byte[] line = (LinkCodec.encode(withId) + "\n").getBytes(StandardCharsets.UTF_8);
            synchronized (out) {
                out.write(line);
                out.flush();
            }
        } catch (IOException ex) {
            inFlight.remove(withId.id());
            future.completeExceptionally(new LinkException("Lost connection to Minecraft."));
            disconnect("Lost connection to Minecraft.");
            return future;
        }
        Duration wait = request.type() == RequestType.UPLOAD_IMAGE ? timeout.multipliedBy(4) : timeout;
        return future.orTimeout(wait.toMillis(), TimeUnit.MILLISECONDS).handle((message, error) -> {
            inFlight.remove(withId.id());
            if (error == null) {
                return message;
            }
            Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                ? error.getCause() : error;
            if (cause instanceof TimeoutException) {
                throw new java.util.concurrent.CompletionException(new LinkException("Minecraft did not answer in time. If the "
                    + "game is paused (it pauses when you switch windows), press F3+P in Minecraft or open the world to LAN."));
            }
            throw cause instanceof java.util.concurrent.CompletionException ce ? ce : new java.util.concurrent.CompletionException(cause);
        });
    }

    /** Sends and waits for the response. */
    public LinkMessage call(LinkRequest request) throws LinkException {
        try {
            return send(request).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LinkException("Interrupted.");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof LinkException link) {
                throw link;
            }
            throw new LinkException(ex.getCause() == null ? "Request failed." : String.valueOf(ex.getCause().getMessage()));
        }
    }

    @Override
    public void close() {
        disconnect("Disconnected.");
    }

    private void readLoop() {
        String reason = "Minecraft closed the connection (the world was closed or the game exited).";
        try {
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            while (open.get()) {
                String line = LinkCodec.readLine(in, LinkProtocol.MAX_MESSAGE_LINE_BYTES);
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                LinkMessage message = LinkCodec.decodeMessage(line);
                if (message.kind() == MessageKind.RESPONSE) {
                    CompletableFuture<LinkMessage> future = message.id() == null ? null : inFlight.get(message.id());
                    if (future != null) {
                        future.complete(message);
                    } else if (!message.isOk() && serverInfo == null) {
                        // error not tied to a request during the handshake (e.g. too many connections)
                        reason = message.message();
                        inFlight.values().forEach(f -> f.complete(message));
                    } else {
                        listener.onEvent(message);
                    }
                } else {
                    listener.onEvent(message);
                }
            }
        } catch (LinkProtocolException ex) {
            reason = "Received an invalid message from Minecraft: " + ex.getMessage();
        } catch (IOException ex) {
            reason = open.get() ? "Lost connection to Minecraft." : "Disconnected.";
        }
        disconnect(reason);
    }

    private void disconnect(String reason) {
        if (open.compareAndSet(true, false)) {
            closeQuietly(socket);
            LinkException error = new LinkException(reason);
            inFlight.values().forEach(future -> future.completeExceptionally(error));
            inFlight.clear();
            listener.onDisconnected(reason);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
