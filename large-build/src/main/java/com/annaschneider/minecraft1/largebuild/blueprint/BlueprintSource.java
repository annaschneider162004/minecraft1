package com.annaschneider.minecraft1.largebuild.blueprint;

/**
 * A blueprint that can be streamed section by section instead of materialising every block in memory.
 * Coordinates are relative to the build origin.
 */
public interface BlueprintSource {
    String name();

    BlockPalette palette();

    Bounds bounds();

    /** Number of sections the cursor will yield (an upper bound; some may be empty). */
    long sectionCount();

    SectionCursor openCursor();
}
