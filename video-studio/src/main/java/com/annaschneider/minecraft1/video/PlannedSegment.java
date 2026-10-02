package com.annaschneider.minecraft1.video;

import java.nio.file.Path;

/**
 * A piece of the final video cut from one footage clip (or a plain title card when {@code clip} is {@code null}).
 *
 * @param sourceStart    seconds into {@code clip} where the piece starts
 * @param sourceDuration seconds of footage used
 * @param outputDuration seconds in the final video; {@code sourceDuration / outputDuration} is the playback speed
 * @param fadeIn         fade-from-black length at the start (0 = hard cut)
 * @param fadeOut        fade-to-black length at the end (0 = hard cut)
 */
public record PlannedSegment(int sceneIndex, Path clip, double sourceStart, double sourceDuration, double outputDuration,
                             double fadeIn, double fadeOut) {
    public PlannedSegment {
        if (!(outputDuration > 0)) {
            throw new IllegalArgumentException("Segment output duration must be positive");
        }
        if (clip != null && !(sourceDuration > 0)) {
            throw new IllegalArgumentException("Segment source duration must be positive");
        }
        sourceStart = Math.max(0, sourceStart);
    }

    public boolean isTitleCard() {
        return clip == null;
    }

    /** Playback speed: 1 = real time, 8 = eight times faster (timelapse), below 1 = slow motion. */
    public double speed() {
        return clip == null ? 1 : sourceDuration / outputDuration;
    }
}
