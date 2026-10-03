package com.annaschneider.minecraft1.mod.fabric;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.mod.runtime.BlockWorld;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real Fabric adapter over {@link ServerWorld} implementing {@link BlockWorld}.
 * Manages chunk tickets so asynchronous or streamed block placement does not cause unsafe synchronous chunk loading.
 */
public final class FabricBlockWorld implements BlockWorld {
    private final ServerWorld world;
    private final Set<ChunkPos> heldTickets = ConcurrentHashMap.newKeySet();

    public FabricBlockWorld(ServerWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public ServerWorld serverWorld() {
        return world;
    }

    @Override
    public String getBlock(Vec3i position) {
        int minY = world.getBottomY();
        int maxY = world.getTopY() - 1;
        if (position.y() < minY || position.y() > maxY) {
            return "minecraft:void_air";
        }
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (!world.getWorldBorder().contains(pos)) {
            return "minecraft:void_air";
        }
        if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            return "minecraft:air";
        }
        Block block = world.getBlockState(pos).getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        return id != null ? id.toString() : "minecraft:air";
    }

    @Override
    public void setBlock(Vec3i position, String blockId) {
        requireServerThread();
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("Block ID cannot be empty.");
        }
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid block identifier '" + blockId + "'. Expected format 'namespace:name'.");
        }
        if (!Registries.BLOCK.containsId(id)) {
            throw new IllegalArgumentException("Unknown block '" + blockId + "'.");
        }
        int minY = world.getBottomY();
        int maxY = world.getTopY() - 1;
        if (position.y() < minY || position.y() > maxY) {
            throw new IllegalArgumentException("Cannot place block at Y=" + position.y()
                + ". World height range is " + minY + ".." + maxY + ".");
        }
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (!world.getWorldBorder().contains(pos)) {
            throw new IllegalArgumentException("Cannot place block at (" + position.x() + ", " + position.y()
                + ", " + position.z() + ") outside world border.");
        }
        Block block = Registries.BLOCK.get(id);
        world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
    }

    @Override
    public int minY() {
        return world.getBottomY();
    }

    @Override
    public int maxY() {
        return world.getTopY() - 1;
    }

    @Override
    public boolean withinBorder(Bounds bounds) {
        var border = world.getWorldBorder();
        return border.contains(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
            && border.contains(new BlockPos(bounds.minX(), bounds.minY(), bounds.maxZ()))
            && border.contains(new BlockPos(bounds.maxX(), bounds.minY(), bounds.minZ()))
            && border.contains(new BlockPos(bounds.maxX(), bounds.minY(), bounds.maxZ()));
    }

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        requireServerThread();
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (heldTickets.add(pos)) {
            world.getChunkManager().addTicket(ChunkTicketType.FORCED, pos, 2, pos);
        }
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public void releaseChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (heldTickets.remove(pos)) {
            world.getChunkManager().removeTicket(ChunkTicketType.FORCED, pos, 2, pos);
        }
    }

    @Override
    public void releaseAllChunks() {
        for (ChunkPos pos : heldTickets) {
            world.getChunkManager().removeTicket(ChunkTicketType.FORCED, pos, 2, pos);
        }
        heldTickets.clear();
    }

    /** World access is only safe on the server thread; desktop requests and ticks are already marshalled there. */
    private void requireServerThread() {
        MinecraftServer server = world.getServer();
        if (server != null && !server.isOnThread()) {
            throw new IllegalStateException("Minecraft world changes must run on the server thread.");
        }
    }

    public Set<ChunkPos> heldTickets() {
        return Set.copyOf(heldTickets);
    }
}
