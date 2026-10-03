package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.domain.MirrorAxis;
import com.annaschneider.minecraft1.largebuild.scene.RegionType;
import com.annaschneider.minecraft1.largebuild.scene.SceneRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/** Six geometries assembled from real ground, streets, habitable buildings and access ramps. */
final class GroundedLayoutGenerator implements LayoutGenerator {
    private final LayoutType type;

    GroundedLayoutGenerator(LayoutType type) { this.type = type; }
    @Override public LayoutType layout() { return type; }
    @Override public String id() { return "grounded-" + type.name().toLowerCase(Locale.ROOT); }
    @Override public int version() { return 1; }
    @Override public boolean compatible(PromptAnalysis prompt, StylePreset style) {
        if (prompt.exclusions().contains(type.name().toLowerCase(Locale.ROOT))) return false;
        if (prompt.terrain().equals("cliff")) return type == LayoutType.CLIFF;
        if (prompt.terrain().equals("hillside")) return type == LayoutType.CLIFF || type == LayoutType.TERRACED;
        return true;
    }

    @Override public List<SceneRegion> generate(PromptAnalysis prompt, StylePreset style, int scale, long seed) {
        Builder b = new Builder(prompt, style, seed);
        switch (type) {
            case GRID -> grid(b, scale);
            case LINEAR -> linear(b, scale);
            case RADIAL -> radial(b, scale);
            case RING -> ring(b, scale);
            case TERRACED -> terraced(b, scale);
            case CLIFF -> cliff(b, scale);
            default -> throw new IllegalArgumentException("Not a grounded layout: " + type);
        }
        return List.copyOf(b.regions);
    }

    private static void grid(Builder b, int scale) {
        int n = 2 + scale, extent = n * 20 + 5;
        b.ground(-5, -5, extent + 10, extent + 10, 0, false);
        for (int i = 0; i <= n; i++) {
            b.road(i * 20, 1, 0, 3, extent);
            b.road(0, 1, i * 20, extent, 3);
        }
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) {
            b.house(x * 20 + 6, 1, z * 20 + 6);
            b.road(x * 20 + 9, 1, z * 20 + 3, 3, 3);
        }
        b.decorate();
    }

    private static void linear(Builder b, int scale) {
        int n = 4 + 2 * scale, length = n * 20 + 10;
        b.ground(-5, -24, length + 10, 49, 0, false);
        b.road(0, 1, -2, length, 5);
        for (int i = 0; i < n; i++) {
            int x = i * 20 + 5;
            b.house(x, 1, -18);
            b.house(x, 1, 10);
            b.road(x - 3, 1, -21, 3, 23);
            b.road(x - 3, 1, -21, 10, 3);
            b.road(x + 3, 1, -19, 3, 2);
            b.road(x + 3, 1, 2, 3, 8);
        }
        b.decorate();
    }

    private static void radial(Builder b, int scale) {
        int rings = 1 + scale / 3, spacing = 18 + b.random.nextInt(9), radius = 28 + rings * spacing;
        b.ground(-radius - 12, -radius - 12, radius * 2 + 25, radius * 2 + 25, 0, false);
        b.road(-radius, 1, -2, radius * 2 + 1, 5);
        b.road(-2, 1, -radius, 5, radius * 2 + 1);
        b.road(-6, 1, -6, 13, 13);
        for (int r = 1; r <= rings; r++) {
            int d = 22 + r * spacing;
            for (int x : new int[]{-d, d}) for (int z : new int[]{-d, d}) {
                b.house(x, 1, z);
                b.road(Math.min(x + 4, 0), 1, z - 3, Math.abs(x + 4) + 3, 3);
                b.road(x + 3, 1, z - 3, 3, 3);
            }
            b.house(d, 1, 8); b.road(d + 3, 1, 3, 3, 5);
            b.house(-d, 1, 8); b.road(-d + 3, 1, 3, 3, 5);
        }
        b.decorate();
    }

    private static void ring(Builder b, int scale) {
        int radius = 32 + scale * 6, outer = radius + 17;
        b.ground(-outer, -outer, outer * 2 + 1, outer * 2 + 1, 0, false);
        b.road(-radius, 1, -radius, radius * 2 + 5, 5);
        b.road(-radius, 1, radius, radius * 2 + 5, 5);
        b.road(-radius, 1, -radius, 5, radius * 2 + 5);
        b.road(radius, 1, -radius, 5, radius * 2 + 5);
        for (int p = -radius + 10; p <= radius - 10; p += 20) {
            b.house(p, 1, -radius - 12);
            b.road(p - 3, 1, -radius - 15, 3, 18);
            b.road(p - 3, 1, -radius - 15, 10, 3);
            b.road(p + 3, 1, -radius - 13, 3, 2);
            b.house(p, 1, radius + 7);
            b.road(p + 3, 1, radius + 4, 3, 3);
            b.house(-radius - 12, 1, p);
            b.road(-radius - 8, 1, p - 3, 10, 3);
            b.road(-radius - 8, 1, p - 3, 3, 3);
            b.house(radius + 7, 1, p);
            b.road(radius + 2, 1, p - 3, 11, 3);
            b.road(radius + 10, 1, p - 3, 3, 3);
        }
        // Deliberately no central building, island or radial cross-road.
        b.decorate();
    }

    private static void terraced(Builder b, int scale) {
        int levels = 3 + Math.min(scale, 12), rows = 2 + scale / 2;
        int rise = 4, band = 24;
        int width = rows * 20 + 10;
        b.ground(-12, -5, levels * band + 24, width + 10, 0, false);
        for (int level = 0; level < levels; level++) {
            int x = level * band, height = level * rise;
            b.ground(x, 0, band, width, height, false);
            b.road(x + (level == 0 ? 0 : rise), height + 1, 1,
                band - (level == 0 ? 0 : rise), 4);
            b.road(x + band - 4, height + 1, 1, 3, width - 1);
            for (int row = 0; row < rows; row++) {
                b.house(x + rise + 3, height + 1, row * 20 + 9);
                b.road(x + rise + 6, height + 1, row * 20 + 6, band - rise - 7, 3);
            }
            if (level > 0) {
                // Ramp clears the riser's solid ground and reaches its street at one block per step.
                b.add(RegionType.STAIR, x, height - rise + 1, 1, rise, rise + 3, 4);
            }
        }
        b.decorate();
    }

    private static void cliff(Builder b, int scale) {
        int levels = 3 + Math.min(scale, 7), rows = 2 + scale / 2;
        int pitch = 24, width = rows * 20 + 34, top = (levels - 1) * pitch;
        b.ground(-12, -5, 44, width + 10, 0, true);
        b.add(RegionType.ROCK, -10, 1, 0, 12, top + 22, width);
        for (int row = 0; row < rows; row++) {
            b.add(RegionType.ROCK, 26, 1, row * 20 + 12, 3, top, 3);
        }
        for (int level = 0; level < levels; level++) {
            int height = level * pitch;
            b.add(RegionType.LEDGE, 0, height - 2, 0, 29, 3, width);
            b.road(13, height + 1, 0, 3, width);
            b.road(13, height + 1, level % 2 == 0 ? 0 : pitch, 13, 1);
            for (int row = 0; row < rows; row++) {
                int z = row * 20 + 10;
                b.house(2, height + 1, z);
                b.road(5, height + 1, z - 3, 11, 3);
                b.road(5, height + 1, z - 1, 3, 1);
            }
            if (level > 0) {
                // Alternating flight channels preserve headroom when each ascent reverses along the facade.
                boolean forward = (level & 1) == 1;
                b.addRotated(RegionType.STAIR, forward ? 20 : 22, height - pitch + 1,
                    forward ? 0 : pitch, pitch, pitch + 3, 4, forward ? 90 : 270);
            }
        }
        b.decorate();
    }

    private static final class Builder {
        final List<SceneRegion> regions = new ArrayList<>();
        final PromptAnalysis prompt;
        final StylePreset style;
        final SplittableRandom random;
        final long seed;
        int counter;
        int buildings;

        Builder(PromptAnalysis prompt, StylePreset style, long seed) {
            this.prompt = prompt; this.style = style; this.seed = seed; random = new SplittableRandom(seed);
        }
        void add(RegionType type, int x, int y, int z, int sx, int sy, int sz) {
            addRotated(type, x, y, z, sx, sy, sz, 0);
        }
        void addRotated(RegionType type, int x, int y, int z, int sx, int sy, int sz, int rotation) {
            regions.add(new SceneRegion(type.id() + "_" + counter++, type, x, y, z, sx, sy, sz, rotation,
                MirrorAxis.NONE, seed ^ counter));
        }
        void ground(int x, int z, int sx, int sz, int top, boolean rock) {
            add(rock ? RegionType.ROCK : RegionType.FOUNDATION, x, -3, z, sx, top + 4, sz);
        }
        void road(int x, int y, int z, int sx, int sz) { add(RegionType.ROAD, x, y, z, sx, 1, sz); }
        void house(int x, int y, int z) {
            if (prompt.exclusions().contains("building")) return;
            int height = (style == StylePreset.INDUSTRIAL ? 6 : 7) + random.nextInt(4);
            boolean tower = style.supportedFeatures().contains("tower") && !prompt.exclusions().contains("tower")
                && (prompt.features().contains("tower") || prompt.subject().equals("castle") || prompt.subject().equals("fortress"))
                && (buildings == 0 || random.nextInt(3) == 0);
            add(tower ? RegionType.TOWER : RegionType.BUILDING, x, y, z, 9, tower ? height + 8 : height, 9);
            buildings++;
        }
        void decorate() {
            List<String> features = List.of("garden", "trees", "water", "wall", "waterfall").stream()
                .filter(this::enabled).toList();
            if (features.isEmpty()) return;
            SceneRegion access = regions.stream().filter(r -> r.type() == RegionType.ROAD).findFirst().orElseThrow();
            int minX = regions.stream().mapToInt(SceneRegion::x).min().orElseThrow();
            int north = regions.stream().mapToInt(SceneRegion::z).min().orElseThrow() - 4;
            int x = minX - features.size() * 10 - 5, y = access.y(), z = access.z() + 8;
            // Separate supported sites outside the occupied footprint; do not cover streets or building doors.
            ground(x, north, access.x() - x + 3, access.z() + 15 - north, y - 1, false);
            // Circulation goes around the north edge, preserving cliff backing instead of tunneling through it.
            accessRoad(x, y, access.z(), features.size() * 10 - 3, 3);
            accessRoad(x + 3, y, north, 3, access.z() - north + 3);
            accessRoad(x + 3, y, north, access.x() - x, 3);
            accessRoad(access.x(), y, north, 3, access.z() - north + 3);
            for (String feature : features) {
                road(x + 3, y, access.z() + 3, 3, 5);
                switch (feature) {
                    case "garden" -> add(RegionType.PLANTING, x, y, z, 7, 2, 7);
                    case "trees" -> add(RegionType.GROVE, x, y, z, 7, 6, 7);
                    case "water" -> add(RegionType.POOL, x, y, z, 7, 2, 7);
                    case "wall" -> add(RegionType.WALL, x, y, z, 7, 4, 1);
                    case "waterfall" -> {
                        add(RegionType.ROCK, x, y, z, 7, 7, 3);
                        add(RegionType.POOL, x, y - 1, z + 3, 7, 2, 4);
                        add(RegionType.WATERFALL, x + 3, y + 6, z + 3, 5, 7, 1);
                    }
                    default -> throw new IllegalStateException(feature);
                }
                x += 10;
            }
        }
        void accessRoad(int x, int y, int z, int sx, int sz) {
            road(x, y, z, sx, sz);
            add(RegionType.CLEARANCE, x, y + 1, z, sx, 2, sz);
        }
        boolean enabled(String feature) {
            return prompt.features().contains(feature) && style.supportedFeatures().contains(feature)
                && !prompt.exclusions().contains(feature)
                && !(feature.equals("waterfall") && prompt.exclusions().contains("water"));
        }
    }
}
