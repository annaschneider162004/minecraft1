package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * A deterministic, bounded structure described as a pure function of local coordinates. Because nothing is
 * materialised up front, generators compose into scenes of tens of millions of blocks while the engine only ever
 * evaluates one 16^3 section at a time.
 */
public interface StructureGenerator {
    String id();

    /** Local bounds; {@link #blockAt} is only called inside these bounds. */
    Bounds localBounds();

    /** Every block id this generator may return (used for palette building and validation). */
    Set<String> palette();

    /** Block id at a local position or {@code null} to leave the world untouched. Must be deterministic. */
    String blockAt(int x, int y, int z);
}
