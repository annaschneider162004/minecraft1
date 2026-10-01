package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.engine.WorldAccess;

/**
 * Adapts the command layer's {@link BlockWorld} to the large-build engine's {@link WorldAccess}.
 */
public final class BlockWorldAccess implements WorldAccess {
    private final BlockWorld world;

    public BlockWorldAccess(BlockWorld world) {
        this.world = world;
    }

    @Override
    public String getBlock(int x, int y, int z) {
        return world.getBlock(new Vec3i(x, y, z));
    }

    @Override
    public void setBlock(int x, int y, int z, String blockId) {
        world.setBlock(new Vec3i(x, y, z), blockId);
    }

    @Override
    public int minY() {
        return world.minY();
    }

    @Override
    public int maxY() {
        return world.maxY();
    }

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        return world.prepareChunk(chunkX, chunkZ);
    }

    @Override
    public void releaseChunk(int chunkX, int chunkZ) {
        world.releaseChunk(chunkX, chunkZ);
    }
}
