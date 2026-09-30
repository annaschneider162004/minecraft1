package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;

import java.util.Objects;

/**
 * image reference -> {@link ImageAnalysisProvider} -> {@link ScenePlanner} -> {@link ScenePlan}.
 */
public final class ImageToBlueprintPipeline {
    private final ImageReferenceResolver resolver;
    private final ImageAnalysisProvider analyzer;
    private final ScenePlanner planner;

    public ImageToBlueprintPipeline(ImageReferenceResolver resolver, ImageAnalysisProvider analyzer, ScenePlanner planner) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public static ImageToBlueprintPipeline deterministic(ImageReferenceResolver resolver) {
        return new ImageToBlueprintPipeline(resolver, new HeuristicImageAnalysisProvider(), new DeterministicScenePlanner());
    }

    public ScenePlan plan(String imageReference, PlanOptions options) {
        ImageReference image = resolver.resolve(imageReference);
        return planner.plan(analyzer.analyze(image), options);
    }
}
