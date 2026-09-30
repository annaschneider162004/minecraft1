package com.annaschneider.minecraft1.largebuild.blueprint;

/**
 * Forward-only iterator over the sections of a blueprint, in packed {@link SectionKey} order.
 */
public interface SectionCursor extends AutoCloseable {
    /**
     * Fills {@code target} with the next section. Returns {@code false} when exhausted. Sections may be empty.
     */
    boolean next(BlockSection target);

    @Override
    void close();
}
