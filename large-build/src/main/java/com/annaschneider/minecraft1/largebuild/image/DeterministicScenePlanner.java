package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.domain.MirrorAxis;
import com.annaschneider.minecraft1.largebuild.generator.Noise;
import com.annaschneider.minecraft1.largebuild.generator.PalaceCoreGenerator;
import com.annaschneider.minecraft1.largebuild.scene.RegionType;
import com.annaschneider.minecraft1.largebuild.scene.SceneRegion;
import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic "floating kingdom" layout: a palace on stepped terraces on a central island, surrounded by rings of
 * floating islands on a grid, linked by a spanning tree of bridges, decorated with paths, gardens, cherry groves and
 * waterfalls, and covered by a cloud layer. Same analysis + options always yield the same plan.
 *
 * <p>{@code scale} is the number of island rings; the grid is stretched to follow the image aspect ratio.
 */
public final class DeterministicScenePlanner implements ScenePlanner {
    public static final String ID = "deterministic-kingdom-v1";
    private static final int TERRACE_HEIGHT = 12;
    private static final int BRIDGE_WIDTH = 7;
    private static final int BRIDGE_ARCH = 10;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ScenePlan plan(ImageAnalysis analysis, PlanOptions options) {
        int scale = options.scale();
        long seed = analysis.seed();
        Set<RegionType> features = analysis.features();
        double aspect = Math.max(0.5, Math.min(3.0, analysis.aspectRatio()));
        int ringsX = aspect >= 1 ? scale : Math.max(1, (int) Math.round(scale * aspect));
        int ringsZ = aspect >= 1 ? Math.max(1, (int) Math.round(scale / aspect)) : scale;

        int palaceHalf = Math.min(64, 28 + 4 * scale);
        int palaceHeight = Math.min(120, 40 + 4 * scale);
        int terraceRadius = palaceHalf + 16;
        int centerRadius = terraceRadius + 20;
        int spacing = roundUp16(centerRadius + 60);

        List<SceneRegion> islands = new ArrayList<>();
        List<SceneRegion> decorations = new ArrayList<>();
        List<SceneRegion> bridges = new ArrayList<>();
        List<SceneRegion> paths = new ArrayList<>();

        islands.add(region("island_center", RegionType.ISLAND, 0, 0, 0, 2 * centerRadius + 1, 50, 2 * centerRadius + 1, 0, seed));
        if (features.contains(RegionType.TERRACE)) {
            decorations.add(region("terrace_center", RegionType.TERRACE, 0, 1, 0, 2 * terraceRadius + 1, TERRACE_HEIGHT, 2 * terraceRadius + 1, 0, seed));
        }
        int palaceY = features.contains(RegionType.TERRACE) ? 1 + TERRACE_HEIGHT : 1;
        int palaceTop = palaceY + new PalaceCoreGenerator(palaceHalf, palaceHeight).localBounds().maxY();
        decorations.add(region("palace_core", RegionType.PALACE_CORE, 0, palaceY, 0, 2 * palaceHalf + 1, palaceHeight, 2 * palaceHalf + 1, 0, seed));

        List<SceneRegion> waterfalls = new ArrayList<>();
        int islandCount = 0;
        for (int i = -ringsX; i <= ringsX; i++) {
            for (int j = -ringsZ; j <= ringsZ; j++) {
                if (i == 0 && j == 0) {
                    continue;
                }
                islandCount++;
                int radius = islandRadius(seed, i, j);
                int depth = 22 + Noise.nextInt(seed, i, 2, j, 19);
                int cx = i * spacing;
                int cz = j * spacing;
                String suffix = coord(i) + "_" + coord(j);
                islands.add(region("island_" + suffix, RegionType.ISLAND, cx, 0, cz, 2 * radius + 1, depth, 2 * radius + 1, 0, seed ^ Noise.hash(seed, i, 0, j)));

                boolean garden = features.contains(RegionType.GARDEN) && Noise.nextInt(seed, i, 3, j, 3) != 0;
                if (garden) {
                    int r = Math.max(4, (int) (radius * 0.45));
                    decorations.add(region("garden_" + suffix, RegionType.GARDEN, cx, 0, cz, 2 * r + 1, 2, 2 * r + 1, 0, Noise.hash(seed, i, 3, j)));
                } else if (features.contains(RegionType.CHERRY_TREES)) {
                    int r = Math.max(4, (int) (radius * 0.7));
                    decorations.add(region("cherry_" + suffix, RegionType.CHERRY_TREES, cx, 0, cz, 2 * r + 1, 10, 2 * r + 1, 0, Noise.hash(seed, i, 4, j)));
                }
                if (features.contains(RegionType.WATERFALL) && j != 0 && Noise.nextInt(seed, i, 5, j, 3) == 0) {
                    int dir = i >= 0 ? 1 : -1;
                    int inner = (int) Math.round(radius * 0.78);
                    int width = 5 + Noise.nextInt(seed, i, 6, j, 5);
                    waterfalls.add(region("waterfall_" + suffix, RegionType.WATERFALL, cx + dir * (inner - 2), 0, cz,
                        width, depth + 12, 1, dir > 0 ? 270 : 90, Noise.hash(seed, i, 6, j)));
                }

                int pi = j != 0 ? i : i - Integer.signum(i);
                int pj = j != 0 ? j - Integer.signum(j) : 0;
                int parentRadius = pi == 0 && pj == 0 ? centerRadius : islandRadius(seed, pi, pj);
                addBridge(bridges, paths, features, suffix, i, j, radius, pi, pj, parentRadius, spacing);
            }
        }

        List<SceneRegion> regions = new ArrayList<>(islands);
        regions.addAll(decorations);
        if (features.contains(RegionType.PATH)) {
            regions.addAll(paths);
        }
        regions.addAll(waterfalls);
        regions.addAll(bridges);
        if (features.contains(RegionType.CLOUDS)) {
            int extentX = ringsX * spacing + spacing / 2;
            int extentZ = ringsZ * spacing + spacing / 2;
            regions.add(region("clouds", RegionType.CLOUDS, -extentX, palaceTop + 12, -extentZ,
                Math.min(SceneRegion.MAX_HORIZONTAL_SIZE, 2 * extentX + 1), 4, Math.min(SceneRegion.MAX_HORIZONTAL_SIZE, 2 * extentZ + 1), 0, seed));
        }

        List<String> notes = new ArrayList<>(analysis.notes());
        notes.add("Planner " + ID + ": " + (islandCount + 1) + " islands on a " + (2 * ringsX + 1) + "x" + (2 * ringsZ + 1)
            + " grid, spacing " + spacing + " blocks, " + bridges.size() + " bridges.");
        String source = analysis.image() == null ? "" : analysis.image().display();
        return new ScenePlan(ScenePlan.FORMAT_VERSION, options.planId(), "Floating kingdom from " + source, source,
            analysis.providerId() + "+" + ID, analysis.style(), seed, scale, regions, notes);
    }

    private static void addBridge(List<SceneRegion> bridges, List<SceneRegion> paths, Set<RegionType> features, String suffix,
                                  int i, int j, int radius, int pi, int pj, int parentRadius, int spacing) {
        boolean alongX = j == 0;
        int childCenter = (alongX ? i : j) * spacing;
        int parentCenter = (alongX ? pi : pj) * spacing;
        int fixed = (alongX ? j : i) * spacing;
        int dir = Integer.signum(parentCenter - childCenter);
        int childReach = (int) (radius * 0.78 * 0.9);
        int parentReach = (int) (parentRadius * 0.78 * 0.9);
        int childEnd = childCenter + dir * childReach;
        int parentEnd = parentCenter - dir * parentReach;
        int start = Math.min(childEnd, parentEnd);
        int length = Math.abs(parentEnd - childEnd) + 1;
        if (length >= 4 && features.contains(RegionType.BRIDGE)) {
            bridges.add(alongX
                ? region("bridge_" + suffix, RegionType.BRIDGE, start, 0, fixed, length, BRIDGE_ARCH, BRIDGE_WIDTH, 0, 0)
                : region("bridge_" + suffix, RegionType.BRIDGE, fixed, 0, start, length, BRIDGE_ARCH, BRIDGE_WIDTH, 90, 0));
        }
        int pathLength = childReach;
        if (pathLength >= 2) {
            int rotation = alongX ? (dir > 0 ? 0 : 180) : (dir > 0 ? 90 : 270);
            int x = alongX ? childCenter : fixed;
            int z = alongX ? fixed : childCenter;
            paths.add(region("path_" + suffix, RegionType.PATH, x, 0, z, pathLength, 1, 3, rotation, i * 31L + j));
        }
    }

    private static int islandRadius(long seed, int i, int j) {
        return 18 + Noise.nextInt(seed, i, 1, j, 13);
    }

    private static SceneRegion region(String id, RegionType type, int x, int y, int z, int sizeX, int sizeY, int sizeZ, int rotation, long seed) {
        return new SceneRegion(id, type, x, y, z, sizeX, sizeY, sizeZ, rotation, MirrorAxis.NONE, seed);
    }

    private static String coord(int value) {
        return value < 0 ? "m" + (-value) : "p" + value;
    }

    private static int roundUp16(int value) {
        return (value + 15) & ~15;
    }
}
