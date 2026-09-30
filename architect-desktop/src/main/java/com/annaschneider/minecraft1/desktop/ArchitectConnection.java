package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.LinkClient;
import com.annaschneider.minecraft1.link.LinkException;
import com.annaschneider.minecraft1.link.LinkInfo;
import com.annaschneider.minecraft1.link.LinkMessage;
import com.annaschneider.minecraft1.link.LinkRequest;
import com.annaschneider.minecraft1.link.RequestType;
import com.annaschneider.minecraft1.link.ServerInfo;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Owns the {@link LinkClient}. All network calls run on one background thread in submission order; every callback
 * is delivered on the Swing event thread.
 */
final class ArchitectConnection {
    enum State { DISCONNECTED, CONNECTING, CONNECTED }

    interface Listener {
        void onStateChanged(State state, String message, ServerInfo server);

        void onEvent(LinkMessage message);
    }

    /** Blocking work to run with a connected client on the background thread. */
    @FunctionalInterface
    interface Task {
        LinkMessage run(LinkClient client) throws LinkException, IOException;
    }

    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "architect-desktop-worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile LinkClient client;
    private volatile State state = State.DISCONNECTED;

    ArchitectConnection(Listener listener) {
        this.listener = listener;
    }

    State state() {
        return state;
    }

    boolean isConnected() {
        LinkClient current = client;
        return current != null && current.isConnected();
    }

    void connect(Path linkFile, String player) {
        if (state == State.CONNECTING) {
            return;
        }
        setState(State.CONNECTING, "Connecting to Minecraft...", null);
        worker.execute(() -> {
            closeClient();
            LinkInfo info;
            try {
                info = LinkInfo.read(linkFile);
            } catch (NoSuchFileException ex) {
                setState(State.DISCONNECTED, "Minecraft is not running with the Architect mod (no link file at " + linkFile
                    + "). Start Minecraft and open a world. Custom launcher? Choose the file in Settings.", null);
                return;
            } catch (IOException ex) {
                setState(State.DISCONNECTED, "Cannot read the link file: " + ex.getMessage(), null);
                return;
            }
            try {
                LinkClient connected = LinkClient.connect(info, "Minecraft Architect desktop", player, new LinkClient.Listener() {
                    @Override
                    public void onEvent(LinkMessage message) {
                        SwingUtilities.invokeLater(() -> listener.onEvent(message));
                    }

                    @Override
                    public void onDisconnected(String reason) {
                        if (client != null) {
                            client = null;
                            setState(State.DISCONNECTED, reason, null);
                        }
                    }
                }, LinkClient.DEFAULT_TIMEOUT);
                client = connected;
                LinkMessage status = connected.call(LinkRequest.of(RequestType.STATUS));
                setState(State.CONNECTED, status.message(), connected.serverInfo());
                if (status.job() != null) {
                    SwingUtilities.invokeLater(() -> listener.onEvent(LinkMessage.progress(status.job())));
                }
            } catch (LinkException ex) {
                closeClient();
                setState(State.DISCONNECTED, ex.getMessage(), null);
            }
        });
    }

    void disconnect() {
        worker.execute(() -> {
            closeClient();
            setState(State.DISCONNECTED, "Disconnected.", null);
        });
    }

    /** Runs {@code task}; exactly one of the callbacks is invoked on the event thread. */
    void submit(Task task, Consumer<LinkMessage> onResult, Consumer<String> onError) {
        worker.execute(() -> {
            LinkClient current = client;
            if (current == null || !current.isConnected()) {
                SwingUtilities.invokeLater(() -> onError.accept("Not connected to Minecraft. Click Connect first."));
                return;
            }
            try {
                LinkMessage result = task.run(current);
                SwingUtilities.invokeLater(() -> onResult.accept(result));
            } catch (LinkException | IOException ex) {
                SwingUtilities.invokeLater(() -> onError.accept(ex.getMessage()));
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> onError.accept("Unexpected error: " + ex));
            }
        });
    }

    void shutdown() {
        closeClient();
        worker.shutdownNow();
    }

    private void closeClient() {
        LinkClient current = client;
        client = null;
        if (current != null) {
            current.close();
        }
    }

    private void setState(State newState, String message, ServerInfo server) {
        state = newState;
        SwingUtilities.invokeLater(() -> listener.onStateChanged(newState, message, server));
    }
}
