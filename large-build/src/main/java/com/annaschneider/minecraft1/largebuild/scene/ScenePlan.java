package com.annaschneider.minecraft1.largebuild.scene;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Provider-neutral high-level description of a scene. Planners (deterministic or AI) produce it, it is persisted as
 * JSON, and {@link SceneCompiler} turns it into a streamable blueprint.
 */
public record ScenePlan(
    int formatVersion,
    String id,
    String title,
    String source,
    String provider,
    String style,
    long seed,
    int scale,
    List<SceneRegion> regions,
    List<String> notes,
    GenerationMetadata metadata
) {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_REGIONS = 50_000;
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public ScenePlan(int formatVersion, String id, String title, String source, String provider, String style,
                     long seed, int scale, List<SceneRegion> regions, List<String> notes) {
        this(formatVersion, id, title, source, provider, style, seed, scale, regions, notes, null);
    }

    public ScenePlan {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported plan format version " + formatVersion + " (expected " + FORMAT_VERSION + ").");
        }
        requireId(id);
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("Plan '" + id + "' has no regions.");
        }
        if (regions.size() > MAX_REGIONS) {
            throw new IllegalArgumentException("Plan '" + id + "' has too many regions (max " + MAX_REGIONS + ").");
        }
        if (regions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Plan '" + id + "' contains an empty region entry.");
        }
        regions = List.copyOf(regions);
        notes = notes == null ? List.of() : List.copyOf(notes);
        title = title == null ? id : title;
        source = source == null ? "" : source;
        provider = provider == null ? "unknown" : provider;
        style = style == null ? "default" : style;
    }

    public static String requireId(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid plan id '" + id + "'. Use 1-64 characters: a-z, 0-9, '_' or '-'.");
        }
        return id;
    }
}
