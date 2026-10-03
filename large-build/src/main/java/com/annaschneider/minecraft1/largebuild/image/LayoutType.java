package com.annaschneider.minecraft1.largebuild.image;

import java.util.Locale;

public enum LayoutType {
    AUTO, RADIAL, LINEAR, TERRACED, RING, GRID, CLIFF, LEGACY;

    public static LayoutType fromId(String value) {
        if (value == null || value.isBlank()) return AUTO;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown layout '" + value + "'. Expected auto, radial, linear, terraced, ring, grid, cliff or legacy.");
        }
    }
}
