package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * White-and-gold palace core: stepped plinth, windowed main hall with stepped roof, central spire tower and corner
 * towers. Local origin is the plinth centre at ground level.
 */
public final class PalaceCoreGenerator implements StructureGenerator {
    private static final String PLINTH = "minecraft:smooth_quartz";
    private static final String WALL = "minecraft:quartz_block";
    private static final String PILLAR = "minecraft:quartz_pillar";
    private static final String WINDOW = "minecraft:glass";
    private static final String GOLD = "minecraft:gold_block";
    private static final String ROOF = "minecraft:yellow_terracotta";
    private static final String RAIL = "minecraft:quartz_slab";

    private final int halfWidth;
    private final int hall;
    private final int wallTop;
    private final int roofTop;
    private final int tower;
    private final int towerTop;
    private final int corner;
    private final int cornerTop;
    private final boolean cornerTowers;
    private final Bounds bounds;

    public PalaceCoreGenerator(int halfWidth, int height) {
        this.halfWidth = Noise.requireRange(halfWidth, 12, 128, "palace halfWidth");
        Noise.requireRange(height, 16, 160, "palace height");
        this.hall = Math.max(6, halfWidth * 3 / 5);
        this.wallTop = 3 + Math.max(8, height * 2 / 5);
        this.roofTop = wallTop + 1 + hall + 2;
        this.tower = Math.max(3, hall / 3);
        this.towerTop = roofTop + Math.max(6, height / 4);
        this.corner = halfWidth - 4;
        this.cornerTop = wallTop + 6;
        this.cornerTowers = corner - 2 > hall + 1;
        int top = Math.max(towerTop + tower + 2 + 5, cornerTop + 3 + 3);
        this.bounds = new Bounds(-halfWidth, 0, -halfWidth, halfWidth, top, halfWidth);
    }

    @Override
    public String id() {
        return "palace";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(PLINTH, WALL, PILLAR, WINDOW, GOLD, ROOF, RAIL);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        int m = Math.max(ax, az);
        if (y <= 2) {
            return m <= halfWidth - y ? PLINTH : null;
        }
        if (cornerTowers) {
            String c = cornerTower(ax, y, az);
            if (c != null) {
                return c;
            }
        }
        if (y > wallTop) {
            String t = centralTower(m, ax, az, y);
            if (t != null) {
                return t;
            }
            int k = y - wallTop - 1;
            int r = hall + 2 - k;
            if (r >= 0 && m <= r && m >= r - 1) {
                return k == 0 || r <= 0 ? GOLD : ROOF;
            }
            return null;
        }
        if (m == hall) {
            if (ax <= 1 && z < 0 && y <= 6) {
                return null;
            }
            int along = ax == hall ? z : x;
            if (ax == hall && az == hall || Math.floorMod(along, 6) == 0) {
                return PILLAR;
            }
            int storey = Math.floorMod(y - 3, 6);
            int slot = Math.floorMod(along, 6);
            if ((storey == 2 || storey == 3) && slot >= 2 && slot <= 4) {
                return WINDOW;
            }
            return WALL;
        }
        if (y == 3) {
            if (m < hall) {
                return PLINTH;
            }
            if (m == halfWidth - 2 && !(ax <= 2 && z < 0)) {
                return RAIL;
            }
        }
        return null;
    }

    private String centralTower(int m, int ax, int az, int y) {
        if (y <= towerTop) {
            if (m == tower) {
                return Math.floorMod(y, 5) == 2 && ax != az ? WINDOW : WALL;
            }
            return null;
        }
        int k = y - towerTop - 1;
        int r = tower + 1 - k;
        if (r >= 0) {
            return m <= r && m >= r - 1 ? (k == 0 ? GOLD : ROOF) : null;
        }
        return ax == 0 && az == 0 && y <= towerTop + tower + 2 + 5 ? GOLD : null;
    }

    private String cornerTower(int ax, int y, int az) {
        int dx = Math.abs(ax - corner);
        int dz = Math.abs(az - corner);
        int m = Math.max(dx, dz);
        if (m > 3) {
            return null;
        }
        if (y <= cornerTop) {
            return m == 2 ? (Math.floorMod(y, 4) == 1 && dx != dz ? WINDOW : WALL) : null;
        }
        int k = y - cornerTop - 1;
        int r = 3 - k;
        if (r >= 0) {
            return m <= r && m >= r - 1 ? (k == 0 ? GOLD : ROOF) : null;
        }
        return m == 0 && y <= cornerTop + 3 + 3 ? GOLD : null;
    }
}
