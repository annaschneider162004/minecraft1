package com.annaschneider.minecraft1.largebuild.blueprint;

import java.util.Arrays;

/**
 * Splits bounding boxes into 16x16x16 sections and chunk columns.
 */
public final class ChunkPartitioner {
    private ChunkPartitioner() {
    }

    public static long sectionCount(Bounds bounds) {
        long sx = Math.floorDiv(bounds.maxX(), 16) - Math.floorDiv(bounds.minX(), 16) + 1L;
        long sy = Math.floorDiv(bounds.maxY(), 16) - Math.floorDiv(bounds.minY(), 16) + 1L;
        long sz = Math.floorDiv(bounds.maxZ(), 16) - Math.floorDiv(bounds.minZ(), 16) + 1L;
        return sx * sy * sz;
    }

    public static long chunkColumnCount(Bounds bounds) {
        long sx = Math.floorDiv(bounds.maxX(), 16) - Math.floorDiv(bounds.minX(), 16) + 1L;
        long sz = Math.floorDiv(bounds.maxZ(), 16) - Math.floorDiv(bounds.minZ(), 16) + 1L;
        return sx * sz;
    }

    /** Packed, sorted section keys that intersect {@code bounds}. */
    public static long[] sectionsIntersecting(Bounds bounds, long maxSections) {
        long count = sectionCount(bounds);
        if (count > maxSections || count > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Region spans " + count + " sections which exceeds the limit of " + maxSections + ".");
        }
        long[] keys = new long[(int) count];
        int i = 0;
        for (int cx = Math.floorDiv(bounds.minX(), 16); cx <= Math.floorDiv(bounds.maxX(), 16); cx++) {
            for (int cz = Math.floorDiv(bounds.minZ(), 16); cz <= Math.floorDiv(bounds.maxZ(), 16); cz++) {
                for (int sy = Math.floorDiv(bounds.minY(), 16); sy <= Math.floorDiv(bounds.maxY(), 16); sy++) {
                    keys[i++] = new SectionKey(cx, sy, cz).pack();
                }
            }
        }
        Arrays.sort(keys);
        return keys;
    }

    /** Sorts and removes duplicates from {@code keys[0..length)}. */
    public static long[] sortedUnique(long[] keys, int length) {
        long[] copy = Arrays.copyOf(keys, length);
        Arrays.sort(copy);
        int unique = 0;
        for (int i = 0; i < copy.length; i++) {
            if (i == 0 || copy[i] != copy[i - 1]) {
                copy[unique++] = copy[i];
            }
        }
        return Arrays.copyOf(copy, unique);
    }

    public static long chunkColumnKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
