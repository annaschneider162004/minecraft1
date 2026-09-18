package com.annaschneider.minecraft1.mod.runtime;

public final class ArchitectConfig {
    public static final int BLOCKS_PER_TICK = clamp(Integer.getInteger("architect.blocksPerTick", 64), 1, 512);

    private ArchitectConfig() {
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
