package com.annaschneider.minecraft1.largebuild.camera;

import java.util.Locale;

/**
 * Live cinematic camera modes. {@link #AUTO} alternates between the other three at bounded intervals.
 */
public enum CameraMode {
    /** No cinematic camera; the player sees through their own eyes. */
    OFF,
    /** Switches between orbit, follow and wide shots while the build runs. */
    AUTO,
    /** Circles the area completed so far. */
    ORBIT,
    /** Stays close to the section currently being built. */
    FOLLOW,
    /** Establishing shot framing the whole known build bounds. */
    WIDE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return this != OFF;
    }

    /** Accepts the mode ids plus the {@code stop} alias of {@link #OFF}. */
    public static CameraMode fromId(String id) {
        if (id != null && ("stop".equalsIgnoreCase(id.trim()) || "none".equalsIgnoreCase(id.trim()))) {
            return OFF;
        }
        if (id != null) {
            for (CameraMode mode : values()) {
                if (mode.name().equalsIgnoreCase(id.trim())) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("Unknown camera mode '" + id + "'. Use auto, orbit, follow, wide or stop.");
    }
}
