package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * Deterministic grove of cherry trees scattered on a jittered grid inside a disc. Trees stand on y=0 (trunks start at
 * y=1).
 */
public final class CherryGroveGenerator implements StructureGenerator {
    private static final String LOG = "minecraft:cherry_log";
    private static final String LEAVES = "minecraft:cherry_leaves";
    private static final int CELL = 9;

    private final int radius;
    private final long seed;
    private final Bounds bounds;

    public CherryGroveGenerator(int radius, long seed) {
        this.radius = Noise.requireRange(radius, 4, 2048, "grove radius");
        this.seed = seed;
        this.bounds = new Bounds(-(radius + 3), 1, -(radius + 3), radius + 3, 10, radius + 3);
    }

    @Override
    public String id() {
        return "cherry";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(LOG, LEAVES);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        String leaves = null;
        for (int i = cx - 1; i <= cx + 1; i++) {
            for (int j = cz - 1; j <= cz + 1; j++) {
                if (Noise.unit(seed, i, 0, j) >= 0.55) {
                    continue;
                }
                int tx = i * CELL + 2 + Noise.nextInt(seed, i, 1, j, 5);
                int tz = j * CELL + 2 + Noise.nextInt(seed, i, 2, j, 5);
                if ((double) tx * tx + (double) tz * tz > (double) (radius - 1) * (radius - 1)) {
                    continue;
                }
                int trunk = 4 + Noise.nextInt(seed, i, 3, j, 3);
                int dx = x - tx;
                int dz = z - tz;
                if (dx == 0 && dz == 0 && y >= 1 && y <= trunk + 1) {
                    return LOG;
                }
                int dy = y - (trunk + 1);
                if (dx * dx / 9.0 + dz * dz / 9.0 + dy * dy / 4.0 <= 1.0) {
                    leaves = LEAVES;
                }
            }
        }
        return leaves;
    }
}
