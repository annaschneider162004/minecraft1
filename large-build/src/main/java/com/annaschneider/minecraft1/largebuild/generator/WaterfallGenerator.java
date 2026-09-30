package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * Vertical water curtain falling from y=0 downwards, facing local +Z, with a stone backing wall at z=-1.
 */
public final class WaterfallGenerator implements StructureGenerator {
    private static final String WATER = "minecraft:water";
    private static final String BACK = "minecraft:stone_bricks";
    private static final String MOSSY = "minecraft:mossy_stone_bricks";

    private final long seed;
    private final Bounds bounds;

    public WaterfallGenerator(int width, int height, long seed) {
        Noise.requireRange(width, 1, 64, "waterfall width");
        Noise.requireRange(height, 2, 256, "waterfall height");
        this.seed = seed;
        int half = width / 2;
        this.bounds = new Bounds(-half, -(height - 1), -1, -half + width - 1, 0, 0);
    }

    @Override
    public String id() {
        return "waterfall";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(WATER, BACK, MOSSY);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        if (z == 0) {
            return WATER;
        }
        return Noise.nextInt(seed, x, y, z, 3) == 0 ? MOSSY : BACK;
    }
}
