package com.annaschneider.minecraft1.video;

import java.util.Objects;

/**
 * One scene of the storyboard. Scenes point at the build by progress (0..1) rather than by footage time, so the same
 * story can be cut from any recording of the build.
 *
 * @param fromProgress    build progress this scene starts at (0..1)
 * @param toProgress      build progress this scene ends at; equal to {@code fromProgress} for a real-time shot
 * @param durationSeconds planned length of the scene in the final video
 */
public record Scene(int index, SceneKind kind, String title, String narration, double fromProgress, double toProgress,
                    double durationSeconds) {
    public Scene {
        Objects.requireNonNull(kind, "kind");
        title = title == null ? "" : title;
        narration = narration == null ? "" : narration;
        fromProgress = clamp01(fromProgress);
        toProgress = Math.max(fromProgress, clamp01(toProgress));
        if (!(durationSeconds > 0)) {
            throw new IllegalArgumentException("Scene duration must be positive");
        }
    }

    public boolean isTimelapse() {
        return kind == SceneKind.TIMELAPSE;
    }

    public Scene withNarration(String text) {
        return new Scene(index, kind, title, text, fromProgress, toProgress, durationSeconds);
    }

    public Scene withTitle(String text) {
        return new Scene(index, kind, text, narration, fromProgress, toProgress, durationSeconds);
    }

    public Scene withDuration(double seconds) {
        return new Scene(index, kind, title, narration, fromProgress, toProgress, seconds);
    }

    private static double clamp01(double value) {
        return Double.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
    }
}
