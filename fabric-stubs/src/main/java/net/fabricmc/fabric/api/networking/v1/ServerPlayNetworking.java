package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ServerPlayNetworking {
    private static final Map<Identifier, PlayChannelHandler> RECEIVERS = new HashMap<>();

    private ServerPlayNetworking() {}

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(
                MinecraftServer server,
                ServerPlayerEntity player,
                ServerPlayNetworkHandler handler,
                PacketByteBuf buf,
                PacketSender responseSender
        );
    }

    public static boolean registerGlobalReceiver(Identifier channelName, PlayChannelHandler channelHandler) {
        RECEIVERS.put(channelName, channelHandler);
        return true;
    }

    public static void unregisterGlobalReceiver(Identifier channelName) {
        RECEIVERS.remove(channelName);
    }

    public static void send(ServerPlayerEntity player, Identifier channelName, PacketByteBuf buf) {
        // Stub: In tests or simulated environment, can forward or record
    }

    public static PlayChannelHandler getReceiver(Identifier channelName) {
        return RECEIVERS.get(channelName);
    }

    public static void clearReceivers() {
        RECEIVERS.clear();
    }
}
