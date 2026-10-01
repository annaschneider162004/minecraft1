package com.annaschneider.minecraft1.mod.recording;

import com.annaschneider.minecraft1.largebuild.engine.BuildListener;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.link.LinkCodec;
import com.annaschneider.minecraft1.link.RecordingStatus;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Server-side coordinator for in-game recording (ReplayMod or compatible client-side recorders).
 * Sends commands to clients over Fabric networking channels and tracks recording status and auto-stop requests.
 */
public final class ServerRecordingCoordinator {
    private static final Logger LOGGER = Logger.getLogger("com.annaschneider.minecraft1.mod.recording");

    private final MinecraftServer server;
    private final Map<UUID, RecordingStatus> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> autoStop = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> pendingBuilds = new ConcurrentHashMap<>();
    private final BuildListener buildListener = new BuildListener() {
        @Override
        public void onJobFinished(JobProgress job) {
            handleJobFinished(job);
        }
    };

    public ServerRecordingCoordinator(MinecraftServer server) {
        this.server = server;
    }

    public BuildListener buildListener() {
        return buildListener;
    }

    public RecordingStatus startRecording(UUID playerId, String playerName) {
        if (isMockMode()) {
            RecordingStatus status = new RecordingStatus(true, true, "RECORDING", "SimulatedRecorder", null, "Recording started.");
            if (playerId != null) {
                statuses.put(playerId, status);
            }
            return status;
        }

        ServerPlayerEntity player = resolvePlayer(playerId, playerName);
        if (player == null) {
            return RecordingStatus.unavailable("Player not connected or not found.");
        }

        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(RecordingChannels.ACTION_START);
            ServerPlayNetworking.send(player, RecordingChannels.RECORD_CMD_CHANNEL, buf);
            RecordingStatus status = new RecordingStatus(true, true, "RECORDING", "ReplayMod", null, "Recording started.");
            statuses.put(player.getUuid(), status);
            return status;
        } catch (Exception ex) {
            LOGGER.warning("Failed to send start recording packet: " + ex.getMessage());
            RecordingStatus status = RecordingStatus.unavailable("Could not reach client recorder: " + ex.getMessage());
            statuses.put(player.getUuid(), status);
            return status;
        }
    }

    public RecordingStatus stopRecording(UUID playerId, String playerName) {
        if (isMockMode()) {
            RecordingStatus status = new RecordingStatus(true, false, "SAVED", "SimulatedRecorder", "replays/simulated.mcpr", "Recording stopped and saved.");
            if (playerId != null) {
                statuses.put(playerId, status);
            }
            return status;
        }

        ServerPlayerEntity player = resolvePlayer(playerId, playerName);
        if (player == null) {
            return RecordingStatus.unavailable("Player not connected or not found.");
        }

        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(RecordingChannels.ACTION_STOP);
            ServerPlayNetworking.send(player, RecordingChannels.RECORD_CMD_CHANNEL, buf);
            RecordingStatus status = new RecordingStatus(true, false, "SAVED", "ReplayMod", "replays/", "Recording stopped and saved.");
            statuses.put(player.getUuid(), status);
            return status;
        } catch (Exception ex) {
            LOGGER.warning("Failed to send stop recording packet: " + ex.getMessage());
            RecordingStatus status = RecordingStatus.unavailable("Could not reach client recorder: " + ex.getMessage());
            statuses.put(player.getUuid(), status);
            return status;
        }
    }

    public RecordingStatus getStatus(UUID playerId) {
        if (playerId != null && statuses.containsKey(playerId)) {
            return statuses.get(playerId);
        }
        if (isMockMode()) {
            return new RecordingStatus(true, false, "IDLE", "SimulatedRecorder", null, "Recording ready.");
        }
        return new RecordingStatus(true, false, "IDLE", "ReplayMod", null, "Recording ready.");
    }

    public void setAutoStop(UUID playerId, boolean enable) {
        if (playerId != null) {
            if (enable) {
                autoStop.put(playerId, true);
            } else {
                autoStop.remove(playerId);
            }
        }
    }

    public boolean isAutoStop(UUID playerId) {
        return playerId != null && autoStop.getOrDefault(playerId, false);
    }

    public void setPendingBuild(UUID playerId, Runnable buildAction) {
        if (playerId != null && buildAction != null) {
            pendingBuilds.put(playerId, buildAction);
        }
    }

    public void onClientStatusReceived(ServerPlayerEntity player, String json) {
        try {
            RecordingStatus status = LinkCodec.decode(json, RecordingStatus.class);
            if (status != null) {
                statuses.put(player.getUuid(), status);
                if (status.recording()) {
                    Runnable pending = pendingBuilds.remove(player.getUuid());
                    if (pending != null) {
                        pending.run();
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.warning("Failed to parse client recording status: " + ex.getMessage());
        }
    }

    private void handleJobFinished(JobProgress job) {
        if (job != null && job.owner() != null && isAutoStop(job.owner())) {
            autoStop.remove(job.owner());
            stopRecording(job.owner(), null);
        }
    }

    private ServerPlayerEntity resolvePlayer(UUID playerId, String playerName) {
        if (server == null || server.getPlayerManager() == null) {
            return null;
        }
        if (playerId != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                return player;
            }
        }
        if (playerName != null && !playerName.isBlank()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
            if (player != null) {
                return player;
            }
        }
        var list = server.getPlayerManager().getPlayerList();
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    private boolean isMockMode() {
        return server == null || Boolean.getBoolean("architect.mockRecording");
    }
}
