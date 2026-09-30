package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Round flower garden on y=0 with gravel cross paths, a moss hedge ring and a lily pond in the middle.
 */
public final class GardenGenerator implements StructureGenerator {
    private static final String GRASS = "minecraft:grass_block";
    private static final String GRAVEL = "minecraft:gravel";
    private static final String HEDGE = "minecraft:moss_block";
    private static final String WATER = "minecraft:water";
    private static final String LILY = "minecraft:lily_pad";
    private static final List<String> FLOWERS = List.of(
        "minecraft:pink_tulip", "minecraft:allium", "minecraft:azure_bluet", "minecraft:lily_of_the_valley", "minecraft:oxeye_daisy");

    private final int radius;
    private final long seed;
    private final Bounds bounds;

    public GardenGenerator(int radius, long seed) {
        this.radius = Noise.requireRange(radius, 4, 2048, "garden radius");
        this.seed = seed;
        this.bounds = new Bounds(-radius, 0, -radius, radius, 1, radius);
    }

    @Override
    public String id() {
        return "garden";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        HashSet<String> set = new HashSet<>(FLOWERS);
        set.addAll(List.of(GRASS, GRAVEL, HEDGE, WATER, LILY));
        return Set.copyOf(set);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d > radius) {
            return null;
        }
        boolean pond = radius >= 8 && d <= radius / 5.0;
        if (pond) {
            if (y == 0) {
                return WATER;
            }
            return Noise.nextInt(seed, x, 7, z, 5) == 0 ? LILY : null;
        }
        boolean path = Math.abs(x) <= 1 || Math.abs(z) <= 1;
        if (y == 0) {
            return path ? GRAVEL : GRASS;
        }
        if (path) {
            return null;
        }
        if (d > radius - 1.5) {
            return HEDGE;
        }
        if (Noise.nextInt(seed, x, 1, z, 4) == 0) {
            return FLOWERS.get(Noise.nextInt(seed, x, 2, z, FLOWERS.size()));
        }
        return null;
    }
}
