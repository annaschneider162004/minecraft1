package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;

/**
 * Turns an {@link ImageAnalysis} into a {@link ScenePlan}. Swap in an AI planner by implementing this interface; the
 * rest of the pipeline (persistence, preview, compile, build) stays unchanged.
 */
public interface ScenePlanner {
    String id();

    ScenePlan plan(ImageAnalysis analysis, PlanOptions options);
}
