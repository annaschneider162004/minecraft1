package com.annaschneider.minecraft1.video;

/** Receives updates from an {@link ExportFlow}. Called from background threads; implementations must be thread-safe. */
public interface FlowListener {
    /** The stage or a job state changed. */
    void status(FlowStatus status);

    /** Detailed progress, for a log or progress bar. */
    void progress(String message);
}
