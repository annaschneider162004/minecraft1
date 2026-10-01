package net.minecraft.server.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public class ServerCommandSource {
    private final MinecraftServer server;
    private final ServerWorld world;
    private final ServerPlayerEntity player;
    private final Vec3d position;
    private String lastFeedback;
    private String lastError;

    public ServerCommandSource(MinecraftServer server, ServerWorld world, ServerPlayerEntity player, Vec3d position) {
        this.server = server;
        this.world = world;
        this.player = player;
        this.position = position;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public Vec3d getPosition() {
        return position;
    }

    public void sendFeedback(Supplier<Text> feedbackSupplier, boolean broadcastToOps) {
        if (feedbackSupplier != null) {
            Text text = feedbackSupplier.get();
            this.lastFeedback = text != null ? text.getString() : "";
            if (player != null && text != null) {
                player.sendMessage(text);
            }
        }
    }

    public void sendError(Text message) {
        this.lastError = message != null ? message.getString() : "";
        if (player != null && message != null) {
            player.sendMessage(message);
        }
    }

    public String getLastFeedback() {
        return lastFeedback;
    }

    public String getLastError() {
        return lastError;
    }
}
