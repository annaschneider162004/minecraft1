package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * Straight walkway along local +X from x=0 at y=0.
 */
public final class PathGenerator implements StructureGenerator {
    private static final String PATH = "minecraft:dirt_path";
    private static final String COARSE = "minecraft:coarse_dirt";

    private final long seed;
    private final Bounds bounds;

    public PathGenerator(int length, int halfWidth, long seed) {
        Noise.requireRange(length, 1, 8192, "path length");
        Noise.requireRange(halfWidth, 0, 8, "path halfWidth");
        this.seed = seed;
        this.bounds = new Bounds(0, 0, -halfWidth, length - 1, 0, halfWidth);
    }

    @Override
    public String id() {
        return "path";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(PATH, COARSE);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        return Noise.nextInt(seed, x, y, z, 4) == 0 ? COARSE : PATH;
    }
}
