package com.annaschneider.minecraft1.largebuild.engine;

/**
 * Minimal, platform-neutral view of a server world. A Fabric implementation wraps {@code ServerWorld}; tests use an
 * in-memory map. All calls happen on the server thread.
 */
public interface WorldAccess {
    String getBlock(int x, int y, int z);

    void setBlock(int x, int y, int z, String blockId);

    default int minY() {
        return -64;
    }

    default int maxY() {
        return 319;
    }

    /**
     * Requests that a chunk column is available for edits. Implementations must not block: return {@code false} to make
     * the engine retry next tick (for example after adding a chunk ticket).
     */
    default boolean prepareChunk(int chunkX, int chunkZ) {
        return true;
    }

    /** Signals that the engine no longer needs the chunk column (e.g. to remove a chunk ticket). */
    default void releaseChunk(int chunkX, int chunkZ) {
    }
}
