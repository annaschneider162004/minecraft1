package com.annaschneider.minecraft1.link;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One desktop client connected to a {@link LinkServer}. Outgoing messages are queued and written by a dedicated
 * thread, so {@link #send} never blocks the game thread; a client that stops reading is disconnected.
 */
public final class LinkConnection {
    private static final int MAX_PENDING_WRITES = 256;
    @SuppressWarnings("StringOperationCanBeSimplified")
    private static final String POISON = new String("<close>");

    private final long id;
    private final Socket socket;
    private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>(MAX_PENDING_WRITES);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile boolean authenticated;
    private volatile String client = "";
    private volatile String player;

    LinkConnection(long id, Socket socket) {
        this.id = id;
        this.socket = socket;
    }

    public long id() {
        return id;
    }

    /** Player requested in {@code hello}, or {@code null} for "the first player online". */
    public String player() {
        return player;
    }

    public String client() {
        return client;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean isOpen() {
        return open.get();
    }

    public void send(LinkMessage message) {
        if (!open.get()) {
            return;
        }
        if (!outbox.offer(LinkCodec.encode(message))) {
            close();
        }
    }

    public void close() {
        if (open.compareAndSet(true, false)) {
            outbox.clear();
            outbox.offer(POISON);
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    void authenticate(LinkRequest hello) {
        client = hello.client() == null ? "" : hello.client();
        player = hello.player();
        authenticated = true;
    }

    BufferedInputStream input() throws IOException {
        return new BufferedInputStream(socket.getInputStream(), 64 * 1024);
    }

    void writeLoop() {
        try (OutputStream out = socket.getOutputStream()) {
            while (true) {
                String line = outbox.take();
                if (line == POISON) {
                    // flush whatever was sent before close()
                    out.flush();
                    return;
                }
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.write('\n');
                if (outbox.isEmpty()) {
                    out.flush();
                }
            }
        } catch (IOException | InterruptedException ex) {
            close();
        }
    }

    /** Sends a final message and closes once it has been written. */
    void sendAndClose(LinkMessage message) {
        if (open.get()) {
            outbox.offer(LinkCodec.encode(message));
            outbox.offer(POISON);
            open.set(false);
        }
    }
}
