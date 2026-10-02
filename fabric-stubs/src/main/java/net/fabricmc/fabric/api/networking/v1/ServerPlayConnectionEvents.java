package net.fabricmc.fabric.api.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;

public final class ServerPlayConnectionEvents {
    public static final Event<Join> JOIN = new Event<>();
    public static final Event<Disconnect> DISCONNECT = new Event<>();

    private ServerPlayConnectionEvents() {}

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server);
    }
}
