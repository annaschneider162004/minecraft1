package com.annaschneider.minecraft1.link;

/** Locally saved camera preferences and bounded live build-worker settings. */
public record CameraNpcSettings(boolean cameraEnabled, boolean npcEnabled, int maxNpcs,
                                int cameraHeight, int rotationSpeed) {
    public CameraNpcSettings {
        maxNpcs = clamp(maxNpcs, 0, 12);
        cameraHeight = clamp(cameraHeight, 5, 80);
        rotationSpeed = clamp(rotationSpeed, 1, 30);
    }

    public static CameraNpcSettings defaults() {
        return new CameraNpcSettings(false, false, 4, 12, 9);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
