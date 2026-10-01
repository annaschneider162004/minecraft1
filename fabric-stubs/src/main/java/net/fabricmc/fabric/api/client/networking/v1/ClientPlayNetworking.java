package net.fabricmc.fabric.api.client.networking.v1;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class ClientPlayNetworking {
    private static final Map<Identifier, PlayChannelHandler> RECEIVERS = new HashMap<>();

    private ClientPlayNetworking() {}

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(
                MinecraftClient client,
                ClientPlayNetworkHandler handler,
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

    public static void send(Identifier channelName, PacketByteBuf buf) {
        // Stub for sending packet from client to server
    }

    public static PlayChannelHandler getReceiver(Identifier channelName) {
        return RECEIVERS.get(channelName);
    }

    public static void clearReceivers() {
        RECEIVERS.clear();
    }
}
