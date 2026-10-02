package com.annaschneider.minecraft1.video;

/**
 * A point on the build timeline.
 *
 * @param timeSeconds seconds since the start of the recorded footage (negative = unknown)
 * @param percent     build progress 0..100
 * @param label       short description such as "started", "50%" or "completed"
 */
public record BuildMilestone(double timeSeconds, double percent, String label) {
    public BuildMilestone {
        if (Double.isNaN(percent)) {
            percent = 0;
        }
        percent = Math.max(0, Math.min(100, percent));
        if (Double.isNaN(timeSeconds)) {
            timeSeconds = -1;
        }
        label = label == null ? "" : label;
    }

    public boolean hasTime() {
        return timeSeconds >= 0;
    }
}
