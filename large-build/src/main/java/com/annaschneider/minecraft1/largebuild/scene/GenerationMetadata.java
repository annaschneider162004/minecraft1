package com.annaschneider.minecraft1.largebuild.scene;

import com.annaschneider.minecraft1.largebuild.image.LayoutType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reproducibility information; the resolved seed is stored on ScenePlan itself. */
public record GenerationMetadata(LayoutType layout, String generatorId, int generatorVersion, String style,
                                 String prompt, String subject, String terrain, List<String> features,
                                 List<String> exclusions, Map<LayoutType, Double> weights,
                                 Map<String, String> parameters) {
    public GenerationMetadata {
        features = features == null ? List.of() : List.copyOf(features);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        EnumMap<LayoutType, Double> ordered = new EnumMap<>(LayoutType.class);
        if (weights != null) ordered.putAll(weights);
        weights = Collections.unmodifiableMap(ordered);
        parameters = Collections.unmodifiableMap(new TreeMap<>(parameters == null ? Map.of() : parameters));
    }
}
