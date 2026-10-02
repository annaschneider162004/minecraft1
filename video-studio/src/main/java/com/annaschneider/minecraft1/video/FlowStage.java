package com.annaschneider.minecraft1.video;

/**
 * Stages of the automatic voice-over export, in order: Idle -> Checking dependencies -> Recording + Narration -> Waiting
 * for remaining job -> Muxing audio and video -> Export complete (or Export failed / Cancelled). Labels are shown in the
 * UI in English or Vietnamese.
 */
public enum FlowStage {
    IDLE("Idle", "S\u1eb5n s\u00e0ng"),
    CHECKING_DEPENDENCIES("Checking dependencies...", "\u0110ang ki\u1ec3m tra ph\u1ee5 thu\u1ed9c..."),
    /** Recording and/or narration running; the label lists the running jobs (see {@link FlowStatus#label}). */
    RECORDING_AND_NARRATION("Recording video... + Generating narration...", "\u0110ang quay video... + \u0110ang t\u1ea1o gi\u1ecdng \u0111\u1ecdc..."),
    WAITING_FOR_REMAINING_JOB("Waiting for remaining job...", "\u0110ang ch\u1edd t\u00e1c v\u1ee5 c\u00f2n l\u1ea1i..."),
    MUXING("Muxing audio and video...", "\u0110ang gh\u00e9p audio v\u00e0 video..."),
    COMPLETE("Export complete", "Xu\u1ea5t video ho\u00e0n t\u1ea5t"),
    /** Followed by the reason, e.g. "Export failed: FFmpeg not found". */
    FAILED("Export failed: ", "Xu\u1ea5t video th\u1ea5t b\u1ea1i: "),
    CANCELLED("Cancelled", "\u0110\u00e3 h\u1ee7y");

    private final String english;
    private final String vietnamese;

    FlowStage(String english, String vietnamese) {
        this.english = english;
        this.vietnamese = vietnamese;
    }

    /** @param language "vi" for Vietnamese, anything else for English */
    public String label(String language) {
        return isVietnamese(language) ? vietnamese : english;
    }

    public boolean finished() {
        return this == COMPLETE || this == FAILED || this == CANCELLED;
    }

    static boolean isVietnamese(String language) {
        return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("vi");
    }
}
