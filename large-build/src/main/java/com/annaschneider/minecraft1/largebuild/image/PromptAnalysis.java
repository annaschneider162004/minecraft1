package com.annaschneider.minecraft1.largebuild.image;

import java.util.List;

public record PromptAnalysis(String prompt, String subject, String terrain, StylePreset style, LayoutType layout,
                             List<String> features, List<String> exclusions, List<String> diagnostics) {
    public PromptAnalysis {
        features = List.copyOf(features);
        exclusions = List.copyOf(exclusions);
        diagnostics = List.copyOf(diagnostics);
    }
}
