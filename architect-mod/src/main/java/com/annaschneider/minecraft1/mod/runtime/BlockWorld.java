package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

public interface BlockWorld {
    String getBlock(Vec3i position);

    void setBlock(Vec3i position, String blockId);

    default int minY() {
        return -64;
    }

    default int maxY() {
        return 319;
    }

    default boolean withinBorder(Bounds bounds) {
        return true;
    }

    default boolean prepareChunk(int chunkX, int chunkZ) {
        return true;
    }

    default void releaseChunk(int chunkX, int chunkZ) {
    }

    default void releaseAllChunks() {
    }
}
