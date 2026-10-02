package com.annaschneider.minecraft1.video;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of an {@link ExportFlow}: the overall stage plus one line each for the recording and narration jobs, which
 * run in parallel.
 *
 * @param failure set when {@code stage} is {@link FlowStage#FAILED}
 */
public record FlowStatus(FlowStage stage, JobState recording, JobState narration, FlowFailure failure) {
    public enum JobState {
        /** The selected mode does not use this job. */
        NOT_USED,
        /** Not started yet (dependencies are being checked). */
        PENDING,
        RUNNING,
        DONE,
        FAILED,
        CANCELLED
    }

    public FlowStatus {
        Objects.requireNonNull(stage, "stage");
        recording = recording == null ? JobState.NOT_USED : recording;
        narration = narration == null ? JobState.NOT_USED : narration;
    }

    public static FlowStatus idle() {
        return new FlowStatus(FlowStage.IDLE, JobState.NOT_USED, JobState.NOT_USED, null);
    }

    /** Overall status label, e.g. "Waiting for remaining job..." or "Export failed: FFmpeg not found". */
    public String label(String language) {
        if (stage == FlowStage.FAILED) {
            return stage.label(language) + (failure == null ? "" : failure.reason(language));
        }
        if (stage == FlowStage.RECORDING_AND_NARRATION) {
            List<String> running = new ArrayList<>();
            if (recording == JobState.RUNNING) {
                running.add(recordingLine(language));
            }
            if (narration == JobState.RUNNING) {
                running.add(narrationLine(language));
            }
            return running.isEmpty() ? stage.label(language) : String.join(" + ", running);
        }
        return stage.label(language);
    }

    public String recordingLine(String language) {
        boolean vi = FlowStage.isVietnamese(language);
        return switch (recording) {
            case NOT_USED -> vi ? "Kh\u00f4ng quay video \u1edf ch\u1ebf \u0111\u1ed9 n\u00e0y" : "Recording: not used in this mode";
            case PENDING -> vi ? "Quay video: ch\u1edd b\u1eaft \u0111\u1ea7u" : "Recording: waiting to start";
            case RUNNING -> vi ? "\u0110ang quay video..." : "Recording video...";
            case DONE -> vi ? "\u0110\u00e3 quay xong video" : "Recording finished";
            case FAILED -> vi ? "Quay video th\u1ea5t b\u1ea1i" : "Recording failed";
            case CANCELLED -> vi ? "\u0110\u00e3 h\u1ee7y quay video" : "Recording cancelled";
        };
    }

    public String narrationLine(String language) {
        boolean vi = FlowStage.isVietnamese(language);
        return switch (narration) {
            case NOT_USED -> vi ? "Kh\u00f4ng l\u1ed3ng ti\u1ebfng \u1edf ch\u1ebf \u0111\u1ed9 n\u00e0y" : "Narration: not used in this mode";
            case PENDING -> vi ? "Gi\u1ecdng \u0111\u1ecdc: ch\u1edd b\u1eaft \u0111\u1ea7u" : "Narration: waiting to start";
            case RUNNING -> vi ? "\u0110ang t\u1ea1o gi\u1ecdng \u0111\u1ecdc..." : "Generating narration...";
            case DONE -> vi ? "Gi\u1ecdng \u0111\u1ecdc \u0111\u00e3 s\u1eb5n s\u00e0ng" : "Narration ready";
            case FAILED -> vi ? "T\u1ea1o gi\u1ecdng \u0111\u1ecdc th\u1ea5t b\u1ea1i" : "Narration failed";
            case CANCELLED -> vi ? "\u0110\u00e3 h\u1ee7y t\u1ea1o gi\u1ecdng \u0111\u1ecdc" : "Narration cancelled";
        };
    }
}
