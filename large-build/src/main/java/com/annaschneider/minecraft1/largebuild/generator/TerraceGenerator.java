package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * Circular stepped terraces rising from y=0: each level is {@code ringWidth} narrower and {@code stepHeight} taller.
 * Only surfaces, retaining walls and the base plate are emitted, keeping block counts proportional to area.
 */
public final class TerraceGenerator implements StructureGenerator {
    private static final String GRASS = "minecraft:grass_block";
    private static final String TRIM = "minecraft:quartz_block";
    private static final String WALL = "minecraft:stone_bricks";

    private final int radius;
    private final int levels;
    private final int stepHeight;
    private final int ringWidth;
    private final Bounds bounds;

    public TerraceGenerator(int radius, int levels, int stepHeight, int ringWidth) {
        this.radius = Noise.requireRange(radius, 8, 2048, "terrace radius");
        this.levels = Noise.requireRange(levels, 1, 8, "terrace levels");
        this.stepHeight = Noise.requireRange(stepHeight, 2, 12, "terrace stepHeight");
        this.ringWidth = Noise.requireRange(ringWidth, 2, 64, "terrace ringWidth");
        if (radius - (levels - 1) * ringWidth < 4) {
            throw new IllegalArgumentException("terrace radius is too small for the requested levels and ring width.");
        }
        this.bounds = new Bounds(-radius, 0, -radius, radius, levels * stepHeight, radius);
    }

    /** Height of the top terrace surface relative to the local origin. */
    public int topHeight() {
        return levels * stepHeight;
    }

    @Override
    public String id() {
        return "terrace";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(GRASS, TRIM, WALL);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d > radius) {
            return null;
        }
        int level = Math.min(levels - 1, (int) Math.floor((radius - d) / ringWidth));
        int outer = radius - level * ringWidth;
        int top = (level + 1) * stepHeight;
        if (y > top) {
            return null;
        }
        boolean edge = d > outer - 1.5;
        if (y == top) {
            return d > outer - 1 ? TRIM : GRASS;
        }
        if (y == 0 || (edge && y >= level * stepHeight)) {
            return WALL;
        }
        return null;
    }
}
