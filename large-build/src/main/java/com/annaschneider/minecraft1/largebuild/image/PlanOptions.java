package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * @param scale 1..24; density/extent for grounded layouts, island rings for the legacy image planner
 * @param layout explicit layout, or AUTO to use a prompt hint followed by seeded weighted selection
 * @param seed optional; image plans inherit the analysis seed, text plans resolve a fresh seed once
 * @param weights optional per-layout overrides of the selected style's built-in weights
 */
public record PlanOptions(String planId, int scale, LayoutType layout, String style, Long seed,
                          Map<LayoutType, Double> weights) {
    public static final int MAX_SCALE = 24;

    public PlanOptions(String planId, int scale) {
        this(planId, scale, LayoutType.AUTO, null, null, Map.of());
    }

    public PlanOptions {
        ScenePlan.requireId(planId);
        if (scale < 1 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Scale must be in range 1.." + MAX_SCALE + ".");
        }
        layout = layout == null ? LayoutType.AUTO : layout;
        EnumMap<LayoutType, Double> copy = new EnumMap<>(LayoutType.class);
        if (weights != null) {
            for (var entry : weights.entrySet()) {
                if (entry.getKey() == null || entry.getKey() == LayoutType.AUTO || entry.getKey() == LayoutType.LEGACY
                    || entry.getValue() == null || !Double.isFinite(entry.getValue()) || entry.getValue() < 0) {
                    throw new IllegalArgumentException("Layout weights require structural layouts and finite non-negative values.");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        weights = Collections.unmodifiableMap(copy);
    }
}
