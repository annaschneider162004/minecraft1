package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.image.StylePreset;
import com.annaschneider.minecraft1.largebuild.scene.SceneRegion;
import java.util.Set;

/** Rectangular ground-based primitives. Origins are their minimum X/Z corner, unlike legacy round regions. */
public final class GroundedStructureGenerator implements StructureGenerator {
    private final SceneRegion region;
    private final StylePreset style;
    private final Bounds bounds;

    public GroundedStructureGenerator(SceneRegion region, StylePreset style) {
        this.region = region;
        this.style = style;
        bounds = Bounds.ofSize(0, 0, 0, region.sizeX(), region.sizeY(), region.sizeZ());
    }

    @Override public String id() { return region.type().id() + "-v1"; }
    @Override public Bounds localBounds() { return bounds; }
    @Override public Set<String> palette() {
        Set<String> materials = switch (region.type()) {
            case FOUNDATION, ROCK -> Set.of(style.ground());
            case LEDGE, WALL -> Set.of(style.masonry());
            case ROAD -> region.sizeY() > 1 ? Set.of(style.road(), "minecraft:air") : Set.of(style.road());
            case STAIR -> Set.of(style.masonry(), "minecraft:air");
            case CLEARANCE -> Set.of("minecraft:air");
            case BUILDING, TOWER -> Set.copyOf(java.util.List.of(style.masonry(), style.timber(), style.roof(),
                "minecraft:glass", "minecraft:air"));
            case POOL -> Set.of(style.masonry(), "minecraft:water");
            case PLANTING -> style == StylePreset.DESERT ? Set.of(style.ground(), "minecraft:dead_bush")
                : Set.of("minecraft:grass_block", "minecraft:poppy");
            case GROVE -> Set.of("minecraft:oak_log", "minecraft:oak_leaves");
            default -> throw new IllegalStateException("Not a grounded primitive: " + region.type());
        };
        return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(materials));
    }

    @Override public String blockAt(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) return null;
        int sx = region.sizeX(), sy = region.sizeY(), sz = region.sizeZ();
        return switch (region.type()) {
            case FOUNDATION, ROCK -> style.ground();
            case LEDGE -> style.masonry();
            case ROAD -> y == 0 ? style.road() : "minecraft:air";
            case STAIR -> y <= Math.min(sy - 1, x) ? style.masonry() : "minecraft:air";
            case CLEARANCE -> "minecraft:air";
            case POOL -> y == 0 || x == 0 || z == 0 || x == sx - 1 || z == sz - 1
                ? style.masonry() : "minecraft:water";
            case PLANTING -> y == 0 ? (style == StylePreset.DESERT ? style.ground() : "minecraft:grass_block")
                : (x + z) % 4 == 0 ? (style == StylePreset.DESERT ? "minecraft:dead_bush" : "minecraft:poppy") : null;
            case GROVE -> {
                boolean trunk = x % 6 == 3 && z % 6 == 3;
                if (y < sy - 2) yield trunk ? "minecraft:oak_log" : null;
                yield Math.floorMod(x - 3, 6) <= 1 && Math.floorMod(z - 3, 6) <= 1
                    ? "minecraft:oak_leaves" : null;
            }
            case WALL -> y == sy - 1 && (x & 1) == 0 ? null : style.masonry();
            case BUILDING, TOWER -> {
                if (y == 0) yield style.timber();
                if (y == sy - 1) yield style.roof();
                boolean edge = x == 0 || x == sx - 1 || z == 0 || z == sz - 1;
                if (!edge) yield "minecraft:air";
                if (z == 0 && x == sx / 2 && y <= 2) yield "minecraft:air";
                if (y >= 3 && y < sy - 2 && y % 3 == 0
                    && (x == sx / 2 || z == sz / 2)) yield "minecraft:glass";
                yield (x == 0 || x == sx - 1) && (z == 0 || z == sz - 1) ? style.timber() : style.masonry();
            }
            default -> throw new IllegalStateException("Not a grounded primitive: " + region.type());
        };
    }
}
