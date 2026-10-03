package com.annaschneider.minecraft1.video.voice;

import java.util.Objects;

/** Audio processing applied equally to voice previews and exported narration. */
public record NarrationAudioOptions(double speed, Effect effect) {
    public NarrationAudioOptions {
        if (!Double.isFinite(speed) || speed < 0.5 || speed > 2.0) {
            throw new IllegalArgumentException("Tốc độ giọng đọc phải từ 0.5 đến 2.0.");
        }
        Objects.requireNonNull(effect, "effect");
    }

    public static NarrationAudioOptions defaults() {
        return new NarrationAudioOptions(1.0, Effect.NONE);
    }

    boolean requiresProcessing() {
        return speed != 1.0 || effect != Effect.NONE;
    }

    public enum Effect {
        NONE("Không có"),
        SOFT_ECHO("Tiếng vang nhẹ");

        private final String label;

        Effect(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
