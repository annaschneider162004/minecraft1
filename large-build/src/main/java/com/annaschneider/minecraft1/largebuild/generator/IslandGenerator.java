package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * Floating island with a grassy top at y=0, noisy outline and a tapering rocky underside.
 */
public final class IslandGenerator implements StructureGenerator {
    private static final String GRASS = "minecraft:grass_block";
    private static final String DIRT = "minecraft:dirt";
    private static final String STONE = "minecraft:stone";
    private static final String ANDESITE = "minecraft:andesite";

    private final int radius;
    private final int depth;
    private final long seed;
    private final Bounds bounds;

    public IslandGenerator(int radius, int depth, long seed) {
        this.radius = Noise.requireRange(radius, 6, 512, "island radius");
        this.depth = Noise.requireRange(depth, 4, 128, "island depth");
        this.seed = seed;
        this.bounds = new Bounds(-radius, -(depth + 4), -radius, radius, 0, radius);
    }

    @Override
    public String id() {
        return "island";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(GRASS, DIRT, STONE, ANDESITE);
    }

    /** Guaranteed solid radius at the top surface (the outline never shrinks below this). */
    public int innerRadius() {
        return (int) Math.floor(radius * 0.78);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        double effective = radius * (0.78 + 0.22 * Noise.value2(seed, x, z, 12));
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d > effective) {
            return null;
        }
        double t = 1 - d / effective;
        double bottom = 1 + depth * Math.pow(t, 1.3) + 3 * Noise.value2(seed + 1, x, z, 6);
        if (-y > bottom) {
            return null;
        }
        if (y == 0) {
            return GRASS;
        }
        if (y >= -3) {
            return DIRT;
        }
        return Noise.nextInt(seed, x, y, z, 10) == 0 ? ANDESITE : STONE;
    }
}
