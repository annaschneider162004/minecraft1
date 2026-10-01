package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.PacketByteBuf;

public final class PacketByteBufs {
    private PacketByteBufs() {}

    public static PacketByteBuf create() {
        return new PacketByteBuf();
    }
}
