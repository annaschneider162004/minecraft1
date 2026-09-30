package com.annaschneider.minecraft1.largebuild.engine;

import java.util.Locale;
import java.util.UUID;

/**
 * Immutable progress snapshot of a queued, running or finished job.
 */
public record JobProgress(
    long jobId,
    UUID owner,
    String name,
    JobKind kind,
    JobState state,
    long unitsDone,
    long unitsTotal,
    long blocksChanged,
    long blocksUnchanged,
    long ticks,
    boolean waitingForChunks,
    String message
) {
    /** Percentage in [0, 100]; units are sections for builds and journal entries for undo. */
    public double percent() {
        if (state == JobState.COMPLETED) {
            return 100.0;
        }
        if (unitsTotal <= 0) {
            return 0.0;
        }
        return Math.min(100.0, 100.0 * unitsDone / unitsTotal);
    }

    public String describe() {
        String unit = kind == JobKind.BUILD ? "sections" : "blocks";
        String text = String.format(Locale.ROOT, "#%d %s '%s' %s %.1f%% (%d/%d %s, %d blocks changed)",
            jobId, kind.name().toLowerCase(Locale.ROOT), name, state.name().toLowerCase(Locale.ROOT), percent(),
            unitsDone, unitsTotal, unit, blocksChanged);
        if (waitingForChunks) {
            text += " [waiting for chunks]";
        }
        if (message != null && !message.isBlank()) {
            text += " - " + message;
        }
        return text;
    }
}
