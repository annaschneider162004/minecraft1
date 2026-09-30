package com.annaschneider.minecraft1.link;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loopback-only TCP server for the desktop app. Network threads only parse and validate; accepted requests are queued
 * and executed on the game thread by {@link #drain}, so the handler may touch the world directly.
 *
 * <p>Every connection must start with a {@code hello} carrying the token from {@link LinkInfo}; anything else closes
 * the connection. Other local programs (or web pages) therefore cannot drive the mod.
 */
public final class LinkServer implements AutoCloseable {
    public static final int MAX_CONNECTIONS = 4;
    private static final int MAX_PENDING_REQUESTS = 64;
    private static final int HELLO_TIMEOUT_MILLIS = 10_000;

    private final int requestedPort;
    private final byte[] token;
    private final BlockingQueue<Pending> pending = new LinkedBlockingQueue<>(MAX_PENDING_REQUESTS);
    private final List<LinkConnection> connections = new CopyOnWriteArrayList<>();
    private final AtomicLong nextConnectionId = new AtomicLong(1);
    private ServerSocket serverSocket;
    private volatile boolean running;

    /** @param port TCP port on 127.0.0.1, or 0 for any free port */
    public LinkServer(int port, String token) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be in range 0..65535");
        }
        this.requestedPort = port;
        this.token = Objects.requireNonNull(token, "token").getBytes(StandardCharsets.UTF_8);
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), 8);
        } catch (IOException ex) {
            socket.close();
            throw new IOException("Could not listen on 127.0.0.1:" + requestedPort + " (" + ex.getMessage()
                + "). Is another Minecraft instance already running? Set -Darchitect.linkPort to another port.", ex);
        }
        serverSocket = socket;
        running = true;
        Thread acceptor = new Thread(this::acceptLoop, "architect-link-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int port() {
        ServerSocket socket = serverSocket;
        return socket == null ? requestedPort : socket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }

    /** Authenticated, open connections. */
    public List<LinkConnection> connections() {
        List<LinkConnection> result = new ArrayList<>(connections.size());
        for (LinkConnection connection : connections) {
            if (connection.isOpen() && connection.isAuthenticated()) {
                result.add(connection);
            }
        }
        return result;
    }

    /**
     * Executes up to {@code maxRequests} queued requests with {@code handler} on the calling (game) thread and sends
     * the responses. Handler exceptions become error responses.
     */
    public int drain(LinkRequestHandler handler, int maxRequests) {
        int handled = 0;
        Pending next;
        while (handled < maxRequests && (next = pending.poll()) != null) {
            handled++;
            if (!next.connection().isOpen()) {
                continue;
            }
            LinkRequest request = next.request();
            LinkMessage response;
            try {
                response = handler.handle(next.connection(), request);
                if (response == null) {
                    response = LinkMessage.error(request.id(), "No response.");
                }
            } catch (RuntimeException ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                response = LinkMessage.error(request.id(), message);
            }
            next.connection().send(new LinkMessage(response.v(), MessageKind.RESPONSE, request.id(), response.ok(),
                response.message(), response.server(), response.job(), response.plan(), response.source()));
        }
        return handled;
    }

    public void broadcast(LinkMessage message) {
        for (LinkConnection connection : connections()) {
            connection.send(message);
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        for (LinkConnection connection : connections) {
            connection.close();
        }
        connections.clear();
        pending.clear();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException ex) {
                if (running) {
                    continue;
                }
                return;
            }
            connections.removeIf(connection -> !connection.isOpen());
            LinkConnection connection = new LinkConnection(nextConnectionId.getAndIncrement(), socket);
            startThread("architect-link-writer-" + connection.id(), connection::writeLoop);
            if (connections.size() >= MAX_CONNECTIONS) {
                connection.sendAndClose(LinkMessage.error(null, "Too many desktop apps are connected (max "
                    + MAX_CONNECTIONS + "). Close one and try again."));
                continue;
            }
            connections.add(connection);
            startThread("architect-link-reader-" + connection.id(), () -> readLoop(connection, socket));
        }
    }

    private void readLoop(LinkConnection connection, Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(HELLO_TIMEOUT_MILLIS);
            BufferedInputStream in = connection.input();
            while (running && connection.isOpen()) {
                String line = LinkCodec.readLine(in, LinkProtocol.MAX_REQUEST_LINE_BYTES);
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                LinkRequest request;
                try {
                    request = RequestValidator.validate(LinkCodec.decodeRequest(line));
                } catch (LinkProtocolException ex) {
                    if (!connection.isAuthenticated()) {
                        connection.sendAndClose(LinkMessage.error(null, ex.getMessage()));
                        break;
                    }
                    connection.send(LinkMessage.error(safeId(line), ex.getMessage()));
                    continue;
                }
                if (!connection.isAuthenticated()) {
                    if (request.type() != RequestType.HELLO || !tokenMatches(request.token())) {
                        connection.sendAndClose(LinkMessage.error(request.id(), "Wrong or missing link token. Restart the "
                            + "desktop app so it reads desktop-link.json again (the token changes every time Minecraft starts)."));
                        break;
                    }
                    connection.authenticate(request);
                    socket.setSoTimeout(0);
                }
                if (!pending.offer(new Pending(connection, request))) {
                    connection.send(LinkMessage.error(request.id(), "Minecraft is busy with other requests. Try again in a moment."));
                }
            }
        } catch (LinkProtocolException ex) {
            connection.sendAndClose(LinkMessage.error(null, ex.getMessage()));
            return;
        } catch (SocketException ex) {
            // disconnected or hello timeout
        } catch (IOException ex) {
            // peer went away
        }
        connection.close();
        connections.remove(connection);
    }

    private boolean tokenMatches(String candidate) {
        return candidate != null && MessageDigest.isEqual(token, candidate.getBytes(StandardCharsets.UTF_8));
    }

    /** Best-effort id of an invalid request, so the client can match the error. */
    private static String safeId(String line) {
        try {
            LinkRequest request = LinkCodec.decodeRequest(line);
            if (request.id() != null && LinkProtocol.REQUEST_ID.matcher(request.id()).matches()) {
                return request.id();
            }
        } catch (LinkProtocolException ignored) {
            // malformed JSON
        }
        return null;
    }

    private static void startThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private record Pending(LinkConnection connection, LinkRequest request) {
    }
}
