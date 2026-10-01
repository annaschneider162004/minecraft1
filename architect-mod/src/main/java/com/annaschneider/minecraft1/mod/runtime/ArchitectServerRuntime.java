package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.link.LinkInfo;
import com.annaschneider.minecraft1.mod.command.ArchitectCommandEngine;
import com.annaschneider.minecraft1.mod.link.DesktopBridge;
import com.annaschneider.minecraft1.mod.link.PlayerDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Everything the server side needs, with the lifecycle the Fabric adapter drives: create on server start,
 * {@link #startLink()}, {@link #tick} at the end of each server tick, {@link #close()} on server stop.
 */
public final class ArchitectServerRuntime implements AutoCloseable {
    private final ArchitectCommandEngine engine;
    private final DesktopBridge bridge;
    private String linkError;

    public ArchitectServerRuntime(Path dataRoot, PlayerDirectory players, String modVersion) {
        this.engine = new ArchitectCommandEngine(dataRoot);
        this.bridge = new DesktopBridge(engine, players, modVersion);
    }

    public ArchitectCommandEngine engine() {
        return engine;
    }

    public DesktopBridge bridge() {
        return bridge;
    }

    /**
     * Starts the desktop link unless {@code -Darchitect.link=false}. Failures (e.g. port in use) are reported via
     * {@link #linkError()} and never stop the game.
     */
    public Optional<LinkInfo> startLink() {
        if (!ArchitectConfig.LINK_ENABLED) {
            linkError = "Desktop link disabled (-Darchitect.link=false).";
            return Optional.empty();
        }
        try {
            linkError = null;
            return Optional.of(bridge.start(ArchitectConfig.LINK_PORT));
        } catch (IOException | RuntimeException ex) {
            linkError = "Desktop link not started: " + ex.getMessage();
            return Optional.empty();
        }
    }

    public Optional<String> linkError() {
        return Optional.ofNullable(linkError);
    }

    public void tick(BlockWorld world) {
        engine.tick(world);
        bridge.tick();
    }

    @Override
    public void close() {
        bridge.close();
        engine.close();
    }
}
