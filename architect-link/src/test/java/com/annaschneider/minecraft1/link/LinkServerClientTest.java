package com.annaschneider.minecraft1.link;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkServerClientTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ServerInfo INFO = new ServerInfo("test", LinkProtocol.VERSION, "Steve", List.of("house"), 16,
        LinkProtocol.MAX_IMAGE_BYTES);

    private LinkInfo info;
    private LinkServer server;
    private Thread gameThread;
    private final AtomicBoolean ticking = new AtomicBoolean(true);
    private final BlockingQueue<LinkMessage> events = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> disconnects = new LinkedBlockingQueue<>();

    private final LinkClient.Listener listener = new LinkClient.Listener() {
        @Override
        public void onEvent(LinkMessage message) {
            events.add(message);
        }

        @Override
        public void onDisconnected(String reason) {
            disconnects.add(reason);
        }
    };

    /** Stands in for the mod: answers on a separate "game" thread, like the Minecraft server tick. */
    private LinkMessage handle(LinkConnection connection, LinkRequest request) {
        assertEquals("fake-game-thread", Thread.currentThread().getName());
        return switch (request.type()) {
            case HELLO -> LinkMessage.ok(null, "Welcome " + connection.client()).withServer(INFO);
            case BUILD -> LinkMessage.ok(null, "queued " + request.template());
            case UNDO -> throw new IllegalStateException("No completed build to undo.");
            default -> LinkMessage.error(null, "unsupported");
        };
    }

    @BeforeEach
    void setUp() throws IOException {
        info = LinkInfo.create(0, "test");
        server = new LinkServer(0, info.token());
        server.start();
        info = info.withPort(server.port());
        gameThread = new Thread(() -> {
            while (ticking.get()) {
                server.drain(this::handle, 16);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }, "fake-game-thread");
        gameThread.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        ticking.set(false);
        gameThread.join();
        server.close();
    }

    @Test
    void handshakeRequestsAndPushedEvents() throws Exception {
        try (LinkClient client = LinkClient.connect(info, "desktop", "Steve", listener, TIMEOUT)) {
            assertEquals(INFO, client.serverInfo());
            LinkMessage built = client.call(LinkRequest.build(BuildMode.TEMPLATE, "house"));
            assertTrue(built.isOk());
            assertEquals("queued house", built.message());

            LinkMessage undo = client.call(LinkRequest.of(RequestType.UNDO));
            assertFalse(undo.isOk());
            assertEquals("No completed build to undo.", undo.message());

            LinkException invalid = assertThrows(LinkException.class, () -> client.call(LinkRequest.build(BuildMode.PLAN, "Bad Id")));
            assertTrue(invalid.getMessage().contains("Plan name"));

            waitFor(() -> server.connections().size() == 1);
            assertEquals("Steve", server.connections().get(0).player());
            server.broadcast(LinkMessage.progress(new JobStatus(1, "house", "build", "running", 50, 1, 2, 10, false, "")));
            LinkMessage event = events.poll(5, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(MessageKind.PROGRESS, event.kind());
            assertEquals(50.0, event.job().percent());
        }
        waitFor(() -> server.connections().isEmpty());
    }

    @Test
    void wrongTokenIsRejected() {
        LinkInfo wrong = new LinkInfo(1, "127.0.0.1", info.port(), "not-the-token", "test");
        LinkException error = assertThrows(LinkException.class, () -> LinkClient.connect(wrong, "desktop", null, listener, TIMEOUT));
        assertTrue(error.getMessage().contains("Wrong or missing link token"), error.getMessage());
    }

    @Test
    void requestsBeforeHelloCloseTheConnection() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", info.port())) {
            OutputStream out = socket.getOutputStream();
            out.write("POST / HTTP/1.1\r\nHost: evil\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String line = LinkCodec.readLine(new BufferedInputStream(socket.getInputStream()), 4096);
            LinkMessage reply = LinkCodec.decodeMessage(line);
            assertFalse(reply.isOk());
            assertEquals(-1, socket.getInputStream().read(), "server closes the connection");
        }
    }

    @Test
    void missingMinecraftGivesFriendlyError() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        LinkInfo nobody = new LinkInfo(1, "127.0.0.1", freePort, "t", "test");
        LinkException error = assertThrows(LinkException.class, () -> LinkClient.connect(nobody, "desktop", null, listener, TIMEOUT));
        assertTrue(error.getMessage().contains("Minecraft is not reachable"), error.getMessage());
    }

    @Test
    void pausedGameTimesOutAndServerShutdownDisconnects() throws Exception {
        LinkClient client = LinkClient.connect(info, "desktop", null, listener, Duration.ofMillis(500));
        ticking.set(false);
        gameThread.join();
        CompletableFuture<LinkMessage> pending = client.send(LinkRequest.of(RequestType.STATUS));
        LinkException timeout = assertThrows(LinkException.class, () -> {
            try {
                pending.get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException ex) {
                throw ex.getCause();
            }
        });
        assertTrue(timeout.getMessage().contains("did not answer"), timeout.getMessage());

        server.close();
        String reason = disconnects.poll(5, TimeUnit.SECONDS);
        assertNotNull(reason);
        assertFalse(client.isConnected());
        assertThrows(Exception.class, () -> client.send(LinkRequest.of(RequestType.STATUS)).get(5, TimeUnit.SECONDS));
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met in time");
            }
            Thread.sleep(10);
        }
    }
}
