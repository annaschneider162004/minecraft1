package net.minecraft.server.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import java.util.HashMap;
import java.util.Map;

public class ServerWorld implements World {
    private final MinecraftServer server;
    private final RegistryKey<World> registryKey;
    private final ServerChunkManager chunkManager = new ServerChunkManager();
    private final WorldBorder worldBorder = new WorldBorder();
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private int bottomY = -64;
    private int topY = 320;

    public ServerWorld(MinecraftServer server, RegistryKey<World> registryKey) {
        this.server = server;
        this.registryKey = registryKey;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ServerChunkManager getChunkManager() {
        return chunkManager;
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return chunkManager.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public boolean setBlockState(BlockPos pos, BlockState state, int flags) {
        if (state.getBlock() == Blocks.AIR) {
            blocks.remove(pos);
        } else {
            blocks.put(pos, state);
        }
        return true;
    }

    @Override
    public int getBottomY() {
        return bottomY;
    }

    public void setBottomY(int bottomY) {
        this.bottomY = bottomY;
    }

    @Override
    public int getTopY() {
        return topY;
    }

    public void setTopY(int topY) {
        this.topY = topY;
    }

    @Override
    public WorldBorder getWorldBorder() {
        return worldBorder;
    }

    @Override
    public boolean isInBuildLimit(BlockPos pos) {
        return pos.getY() >= bottomY && pos.getY() < topY;
    }

    @Override
    public RegistryKey<World> getRegistryKey() {
        return registryKey;
    }
}
