package net.minecraft.server.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class ServerPlayerEntity {
    private final MinecraftServer server;
    private final ServerWorld world;
    private final UUID uuid;
    private final String name;
    private BlockPos pos = new BlockPos(0, 64, 0);

    public ServerPlayerEntity(MinecraftServer server, ServerWorld world, UUID uuid, String name) {
        this.server = server;
        this.world = world;
        this.uuid = uuid;
        this.name = name;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ServerWorld getServerWorld() {
        return world;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getEntityName() {
        return name;
    }

    public Text getName() {
        return Text.literal(name);
    }

    public int getBlockX() {
        return pos.getX();
    }

    public int getBlockY() {
        return pos.getY();
    }

    public int getBlockZ() {
        return pos.getZ();
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public void setBlockPos(BlockPos pos) {
        this.pos = pos;
    }

    public void sendMessage(Text message) {
    }

    public void sendMessage(Text message, boolean overlay) {
    }
}
