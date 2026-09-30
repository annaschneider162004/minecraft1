package com.annaschneider.minecraft1.link;

import java.util.List;

/**
 * Preview of a plan or template: a human-readable summary plus region footprints for a top-down map. Coordinates are
 * relative to the build origin (the player's position, snapped to the chunk grid for plans).
 */
public record PlanSummary(
    String planId,
    String title,
    String text,
    int sizeX,
    int sizeY,
    int sizeZ,
    long estimatedBlocks,
    List<RegionBox> regions
) {
    public PlanSummary {
        regions = regions == null ? List.of() : List.copyOf(regions);
    }
}
