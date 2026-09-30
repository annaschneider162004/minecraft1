package com.annaschneider.minecraft1.largebuild.generator;

/**
 * Small deterministic hash/value-noise helpers (no global state, no {@code java.util.Random}).
 */
public final class Noise {
    private Noise() {
    }

    public static long hash(long seed, int x, int y, int z) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h ^= x * 0xC2B2AE3D27D4EB4FL;
        h = mix(h);
        h ^= y * 0x165667B19E3779F9L;
        h = mix(h);
        h ^= z * 0x27D4EB2F165667C5L;
        return mix(h);
    }

    /** Uniform value in [0, 1). */
    public static double unit(long seed, int x, int y, int z) {
        return (hash(seed, x, y, z) >>> 11) * 0x1.0p-53;
    }

    /** Non-negative bounded integer in [0, bound). */
    public static int nextInt(long seed, int x, int y, int z, int bound) {
        return (int) Math.floorMod(hash(seed, x, y, z), (long) bound);
    }

    /** Smooth 2D value noise in [0, 1) with lattice cell size {@code cell}. */
    public static double value2(long seed, int x, int z, int cell) {
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        double fx = smooth((x - cx * (double) cell) / cell);
        double fz = smooth((z - cz * (double) cell) / cell);
        double a = unit(seed, cx, 0, cz);
        double b = unit(seed, cx + 1, 0, cz);
        double c = unit(seed, cx, 0, cz + 1);
        double d = unit(seed, cx + 1, 0, cz + 1);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static int requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in range " + min + ".." + max + " (was " + value + ").");
        }
        return value;
    }
}
