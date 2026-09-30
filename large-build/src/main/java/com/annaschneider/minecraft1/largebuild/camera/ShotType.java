package com.annaschneider.minecraft1.largebuild.camera;

import java.util.Locale;

public enum ShotType {
    ORBIT,
    FLYBY,
    TOP_DOWN,
    REVEAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static ShotType fromId(String id) {
        for (ShotType type : values()) {
            if (type.id().equalsIgnoreCase(id) || type.name().equalsIgnoreCase(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown shot type '" + id + "'. Use orbit, flyby, top-down or reveal.");
    }
}
