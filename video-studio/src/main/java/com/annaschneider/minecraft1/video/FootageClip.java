package com.annaschneider.minecraft1.video;

import java.nio.file.Path;
import java.util.Objects;

/** A recorded video file (e.g. a ReplayMod render) and its length. Clips are played back to back in list order. */
public record FootageClip(Path file, double durationSeconds) {
    public FootageClip {
        Objects.requireNonNull(file, "file");
        if (!(durationSeconds > 0)) {
            throw new IllegalArgumentException("Footage duration must be positive: " + file);
        }
    }
}
