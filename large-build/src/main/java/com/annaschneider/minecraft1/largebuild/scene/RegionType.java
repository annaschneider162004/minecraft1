package com.annaschneider.minecraft1.largebuild.scene;

import java.util.Locale;
import java.util.Optional;

/**
 * Kinds of scene regions a planner may emit. Size semantics per type are documented on {@link SceneRegion}.
 */
public enum RegionType {
    PALACE_CORE("palace"),
    BRIDGE("bridge"),
    TERRACE("terrace"),
    WATERFALL("waterfall"),
    ISLAND("island"),
    PATH("path"),
    GARDEN("garden"),
    CHERRY_TREES("cherry"),
    CLOUDS("clouds");

    private final String id;

    RegionType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<RegionType> fromId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (RegionType type : values()) {
            if (type.id.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
