package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * Sparse, puffy cloud layer spanning [0, sizeX) x [0, sizeZ) with up to {@code thickness} layers of wool.
 */
public final class CloudLayerGenerator implements StructureGenerator {
    private static final String CLOUD = "minecraft:white_wool";

    private final int thickness;
    private final double threshold;
    private final long seed;
    private final Bounds bounds;

    /**
     * @param coverage fraction in percent (5..60) of the layer area that should be covered by clouds (approximate)
     */
    public CloudLayerGenerator(int sizeX, int sizeZ, int thickness, int coverage, long seed) {
        Noise.requireRange(sizeX, 16, 16384, "cloud sizeX");
        Noise.requireRange(sizeZ, 16, 16384, "cloud sizeZ");
        this.thickness = Noise.requireRange(thickness, 1, 8, "cloud thickness");
        Noise.requireRange(coverage, 5, 60, "cloud coverage");
        this.threshold = 1.0 - coverage / 100.0 * 0.9 - 0.05;
        this.seed = seed;
        this.bounds = new Bounds(0, 0, 0, sizeX - 1, thickness - 1, sizeZ - 1);
    }

    @Override
    public String id() {
        return "clouds";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(CLOUD);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        double v = 0.7 * Noise.value2(seed, x, z, 32) + 0.3 * Noise.value2(seed + 7, x, z, 8);
        double stretched = (v - 0.2) / 0.6;
        if (stretched <= threshold) {
            return null;
        }
        int columnHeight = (int) Math.ceil((stretched - threshold) / Math.max(0.05, 1 - threshold) * thickness * 2);
        return y < Math.min(thickness, columnHeight) ? CLOUD : null;
    }
}
