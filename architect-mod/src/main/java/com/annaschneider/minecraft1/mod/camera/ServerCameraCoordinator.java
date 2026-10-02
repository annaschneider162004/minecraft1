package com.annaschneider.minecraft1.mod.camera;

import com.annaschneider.minecraft1.largebuild.camera.BuildCameraDirector;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.camera.CameraUpdate;
import com.annaschneider.minecraft1.largebuild.engine.BuildQueue;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.mod.runtime.ArchitectConfig;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.link.LinkCodec;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Server side of the cinematic camera: remembers the mode every player chose and pushes bounded, coalesced job
 * snapshots to that player's client. The camera itself lives entirely on the client; this class only ever sends
 * packets, so a dedicated server never touches client classes.
 * <p>
 * Call {@link #tick(BuildQueue)} at the end of every server tick (server thread).
 */
public final class ServerCameraCoordinator {
    private static final Logger LOGGER = Logger.getLogger("com.annaschneider.minecraft1.mod.camera");

    private final MinecraftServer server;
    private final BuildCameraDirector director;
    private final Map<UUID, CameraMode> modes = new ConcurrentHashMap<>();
    private final Map<UUID, CameraNpcSettings> settings = new ConcurrentHashMap<>();
    private int ticks;

    public ServerCameraCoordinator(MinecraftServer server, BuildCameraDirector director) {
        this.server = server;
        this.director = Objects.requireNonNull(director, "director");
    }

    public CameraMode mode(UUID playerId) {
        if (playerId == null) {
            return CameraMode.OFF;
        }
        return modes.getOrDefault(playerId, ArchitectConfig.defaultCameraMode());
    }

    public boolean isEnabled() {
        return ArchitectConfig.CAMERA_ENABLED;
    }

    public CameraNpcSettings settings(UUID playerId) {
        return playerId == null ? CameraNpcSettings.defaults()
            : settings.getOrDefault(playerId, new CameraNpcSettings(true, ArchitectConfig.NPC_ENABLED,
                ArchitectConfig.NPC_MAX_WORKERS, ArchitectConfig.CAMERA_ORBIT_HEIGHT, ArchitectConfig.CAMERA_ORBIT_SPEED));
    }

    /**
     * Switches a player's camera mode and tells their client about it.
     *
     * @return a user-facing message describing the new state
     */
    public String setMode(UUID playerId, CameraMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (playerId == null) {
            return "No player to film.";
        }
        if ((!ArchitectConfig.CAMERA_ENABLED || (settings.containsKey(playerId) && !settings.get(playerId).cameraEnabled()))
            && mode.isActive()) {
            return "The cinematic camera is disabled in settings or on this server.";
        }
        modes.put(playerId, mode);
        boolean delivered = send(playerId, CameraChannels.CAMERA_MODE_CHANNEL, CameraPacket.ofMode(mode));
        if (mode.isActive()) {
            // give the client the current job immediately so it can start framing without waiting for the next section
            director.snapshot(playerId).ifPresent(update -> sendState(playerId, update));
        }
        if (!delivered) {
            return mode.isActive()
                ? "Camera mode '" + mode.id() + "' saved, but your client did not receive it (is the Architect mod "
                    + "installed on the client?)."
                : "Cinematic camera stopped.";
        }
        return mode.isActive()
            ? "Cinematic camera: " + mode.id() + "."
            : "Cinematic camera stopped; your normal view is restored.";
    }

    public void applySettings(UUID playerId, CameraNpcSettings value) {
        if (playerId == null) {
            return;
        }
        settings.put(playerId, value);
        ServerPlayerEntity player = player(playerId);
        if (player != null) {
            try {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(LinkCodec.encode(value));
                ServerPlayNetworking.send(player, CameraChannels.CAMERA_SETTINGS_CHANNEL, buf);
            } catch (RuntimeException ex) {
                LOGGER.warning("[Architect] Could not send camera settings: " + ex);
            }
        }
        if (!value.cameraEnabled() && mode(playerId).isActive()) {
            setMode(playerId, CameraMode.OFF);
        }
    }

    /** Pushes coalesced camera updates to every player that enabled the camera. */
    public void tick(BuildQueue queue) {
        if (queue == null || modes.isEmpty()) {
            return;
        }
        if (++ticks % ArchitectConfig.CAMERA_UPDATE_TICKS != 0) {
            return;
        }
        for (Map.Entry<UUID, CameraMode> entry : modes.entrySet()) {
            if (!entry.getValue().isActive()) {
                continue;
            }
            UUID playerId = entry.getKey();
            Optional<JobProgress> progress = queue.progress(playerId);
            progress.ifPresent(director::refresh);
            director.pollUpdate(playerId).ifPresent(update -> sendState(playerId, update));
        }
    }

    /** Stops and forgets a player's camera (disconnect, dimension change, server stop). */
    public void forget(UUID playerId) {
        if (playerId != null) {
            modes.remove(playerId);
            settings.remove(playerId);
            director.forget(playerId);
        }
    }

    public void shutdown() {
        for (UUID playerId : Map.copyOf(modes).keySet()) {
            send(playerId, CameraChannels.CAMERA_MODE_CHANNEL, CameraPacket.ofMode(CameraMode.OFF));
        }
        modes.clear();
        settings.clear();
        director.forgetAll();
    }

    private void sendState(UUID playerId, CameraUpdate update) {
        send(playerId, CameraChannels.CAMERA_STATE_CHANNEL, CameraPacket.ofUpdate(update, worldId(playerId)));
    }

    private String worldId(UUID playerId) {
        ServerPlayerEntity player = player(playerId);
        return player == null ? null : player.getServerWorld().getRegistryKey().getValue().toString();
    }

    private boolean send(UUID playerId, net.minecraft.util.Identifier channel, CameraPacket packet) {
        ServerPlayerEntity player = player(playerId);
        if (player == null) {
            return false;
        }
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(packet.encode());
            ServerPlayNetworking.send(player, channel, buf);
            return true;
        } catch (RuntimeException ex) {
            LOGGER.warning("[Architect] Could not send a camera packet: " + ex);
            return false;
        }
    }

    private ServerPlayerEntity player(UUID playerId) {
        if (server == null || playerId == null || server.getPlayerManager() == null) {
            return null;
        }
        return server.getPlayerManager().getPlayer(playerId);
    }
}
