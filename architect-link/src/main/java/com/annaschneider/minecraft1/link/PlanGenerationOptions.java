package com.annaschneider.minecraft1.link;

import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Optional generation choices. Null layout/style/seed use the generator's defaults. */
public record PlanGenerationOptions(String layout, String style, Long seed, Map<String, Double> weights) {
    public static final Set<String> LAYOUTS =
        Set.of("RADIAL", "LINEAR", "TERRACED", "RING", "GRID", "CLIFF", "LEGACY");
    public static final Set<String> WEIGHT_LAYOUTS =
        Set.of("RADIAL", "LINEAR", "TERRACED", "RING", "GRID", "CLIFF");
    public static final Set<String> STYLES = Set.of("fantasy", "medieval", "desert", "industrial", "neutral");

    public PlanGenerationOptions {
        layout = layout == null ? null : layout.trim().toUpperCase(Locale.ROOT);
        style = style == null ? null : style.trim().toLowerCase(Locale.ROOT);
        if ("AUTO".equals(layout)) {
            layout = null;
        }
        if ("auto".equals(style)) {
            style = null;
        }
        require(layout == null || LAYOUTS.contains(layout),
            "Unknown layout. Choose Auto, RADIAL, LINEAR, TERRACED, RING, GRID, CLIFF or LEGACY.");
        require(style == null || STYLES.contains(style),
            "Unknown style. Choose Auto, fantasy, medieval, desert, industrial or neutral.");
        if (weights != null) {
            Map<String, Double> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().trim().toUpperCase(Locale.ROOT);
                require(key != null && WEIGHT_LAYOUTS.contains(key),
                    "Unknown layout weight. Choose RADIAL, LINEAR, TERRACED, RING, GRID or CLIFF.");
                Double value = entry.getValue();
                require(value != null && Double.isFinite(value) && value >= 0,
                    "Layout weights must be finite, nonnegative numbers.");
                require(normalized.putIfAbsent(key, value) == null, "Duplicate layout weight: " + key);
            }
            weights = Map.copyOf(normalized);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new LinkProtocolException(message);
        }
    }
}
