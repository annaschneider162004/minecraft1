package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

/**
 * Cheap deterministic lattice-sampling estimate of how many blocks a generator emits.
 */
public final class BlockEstimator {
    private BlockEstimator() {
    }

    public static long estimate(StructureGenerator generator, int samplesPerAxis) {
        Bounds b = generator.localBounds();
        int n = Math.max(1, samplesPerAxis);
        int nx = Math.min(n, b.sizeX());
        int ny = Math.min(n, b.sizeY());
        int nz = Math.min(n, b.sizeZ());
        long hits = 0;
        for (int i = 0; i < nx; i++) {
            int x = b.minX() + (int) (((long) b.sizeX() * (2L * i + 1)) / (2L * nx));
            for (int j = 0; j < ny; j++) {
                int y = b.minY() + (int) (((long) b.sizeY() * (2L * j + 1)) / (2L * ny));
                for (int k = 0; k < nz; k++) {
                    int z = b.minZ() + (int) (((long) b.sizeZ() * (2L * k + 1)) / (2L * nz));
                    if (generator.blockAt(x, y, z) != null) {
                        hits++;
                    }
                }
            }
        }
        long samples = (long) nx * ny * nz;
        return Math.round((double) hits * b.volume() / samples);
    }
}
