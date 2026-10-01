package net.minecraft.server.network;

public class ServerPlayNetworkHandler {
    public ServerPlayerEntity player;

    public ServerPlayNetworkHandler() {
    }

    public ServerPlayNetworkHandler(ServerPlayerEntity player) {
        this.player = player;
    }
}
