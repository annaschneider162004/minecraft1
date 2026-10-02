package com.annaschneider.minecraft1.video;

import java.util.Objects;

/**
 * Why an {@link ExportFlow} stopped: a short reason for the "Export failed: &lt;reason&gt;" label plus details for the log.
 */
public record FlowFailure(Kind kind, String detail) {
    public enum Kind {
        FFMPEG_MISSING("FFmpeg not found", "Kh\u00f4ng t\u00ecm th\u1ea5y FFmpeg"),
        NARRATION_UNAVAILABLE("Narration backend unavailable", "Backend gi\u1ecdng \u0111\u1ecdc kh\u00f4ng kh\u1ea3 d\u1ee5ng"),
        RECORDING_FAILED("Recording failed", "Quay video th\u1ea5t b\u1ea1i"),
        NARRATION_FAILED("Narration failed", "T\u1ea1o gi\u1ecdng \u0111\u1ecdc th\u1ea5t b\u1ea1i"),
        MUX_FAILED("Failed to mux audio and video", "Kh\u00f4ng th\u1ec3 gh\u00e9p audio v\u00e0 video"),
        OUTPUT_FOLDER("Output folder is not writable", "Kh\u00f4ng th\u1ec3 ghi v\u00e0o th\u01b0 m\u1ee5c \u0111\u1ea7u ra");

        private final String english;
        private final String vietnamese;

        Kind(String english, String vietnamese) {
            this.english = english;
            this.vietnamese = vietnamese;
        }

        public String reason(String language) {
            return FlowStage.isVietnamese(language) ? vietnamese : english;
        }
    }

    public FlowFailure {
        Objects.requireNonNull(kind, "kind");
        detail = detail == null ? "" : detail.strip();
    }

    /** Short reason, e.g. "FFmpeg not found". */
    public String reason(String language) {
        return kind.reason(language);
    }

    /** Reason plus details, for the log and the error dialog. */
    public String describe(String language) {
        return detail.isEmpty() ? reason(language) : reason(language) + " - " + detail;
    }
}
