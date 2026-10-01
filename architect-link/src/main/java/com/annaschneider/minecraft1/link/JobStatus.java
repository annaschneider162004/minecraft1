package com.annaschneider.minecraft1.link;

import java.util.Locale;

/**
 * Wire form of a build/undo job's progress.
 *
 * @param kind  {@code build} or {@code undo}
 * @param state {@code queued}, {@code running}, {@code paused}, {@code completed}, {@code cancelled} or {@code failed}
 */
public record JobStatus(
    long jobId,
    String name,
    String kind,
    String state,
    double percent,
    long unitsDone,
    long unitsTotal,
    long blocksChanged,
    boolean waitingForChunks,
    String message
) {
    public boolean isActive() {
        return "queued".equals(state) || "running".equals(state) || "paused".equals(state);
    }

    public boolean isPaused() {
        return "paused".equals(state);
    }

    public String describe() {
        String text = String.format(Locale.ROOT, "#%d %s '%s' %s %.1f%% (%,d blocks changed)", jobId, kind, name, state,
            percent, blocksChanged);
        if (waitingForChunks) {
            text += " - waiting for chunks to load";
        }
        if (message != null && !message.isBlank()) {
            text += " - " + message;
        }
        return text;
    }
}
