package com.annaschneider.minecraft1.largebuild.engine;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

/**
 * Observer hooks for systems that follow the build (future NPC animation, cinematic camera, progress HUD). Called on
 * the server thread; implementations must be cheap.
 */
public interface BuildListener {
    default void onJobStarted(JobProgress job, Bounds worldBounds) {
    }

    /** A build job finished a 16^3 section; {@code sectionWorldBounds} is in world coordinates. */
    default void onSectionCompleted(JobProgress job, Bounds sectionWorldBounds) {
    }

    default void onJobFinished(JobProgress job) {
    }
}
