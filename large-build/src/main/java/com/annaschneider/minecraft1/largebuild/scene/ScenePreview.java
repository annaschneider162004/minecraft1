package com.annaschneider.minecraft1.largebuild.scene;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.PlacedStructure;
import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.generator.BlockEstimator;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Human-readable summary of a compiled plan (no blocks are generated except for sparse estimation samples).
 */
public record ScenePreview(
    String planId,
    String title,
    int regionCount,
    Map<RegionType, Integer> regionsByType,
    Bounds bounds,
    long sectionCount,
    long chunkColumns,
    long estimatedBlocks
) {
    private static final int SAMPLES_PER_AXIS = 8;

    public static ScenePreview of(ScenePlan plan, ProceduralBlueprint blueprint) {
        Map<RegionType, Integer> byType = new EnumMap<>(RegionType.class);
        for (SceneRegion region : plan.regions()) {
            byType.merge(region.type(), 1, Integer::sum);
        }
        long estimate = 0;
        Set<Long> columns = new HashSet<>();
        for (PlacedStructure structure : blueprint.structures()) {
            estimate += BlockEstimator.estimate(structure.generator(), SAMPLES_PER_AXIS);
            Bounds b = structure.bounds();
            for (int cx = Math.floorDiv(b.minX(), 16); cx <= Math.floorDiv(b.maxX(), 16); cx++) {
                for (int cz = Math.floorDiv(b.minZ(), 16); cz <= Math.floorDiv(b.maxZ(), 16); cz++) {
                    columns.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
                }
            }
        }
        return new ScenePreview(plan.id(), plan.title(), plan.regions().size(), byType, blueprint.bounds(),
            blueprint.sectionCount(), columns.size(), estimate);
    }

    public String describe() {
        String types = regionsByType.entrySet().stream()
            .map(e -> e.getKey().id() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
        return String.format(Locale.ROOT,
            "Plan '%s' (%s): %d regions [%s]%nSize %dx%dx%d (x %d..%d, y %d..%d, z %d..%d)%n%d sections in %d chunk columns, ~%,d blocks (estimate)",
            planId, title, regionCount, types,
            bounds.sizeX(), bounds.sizeY(), bounds.sizeZ(),
            bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), bounds.maxZ(),
            sectionCount, chunkColumns, estimatedBlocks);
    }
}
