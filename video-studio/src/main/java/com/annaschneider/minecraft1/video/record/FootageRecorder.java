package com.annaschneider.minecraft1.video.record;

import com.annaschneider.minecraft1.video.VideoExportException;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/** Records gameplay footage into a video file (the "recording job" of the automatic export). */
public interface FootageRecorder {
    /** Short name for logs, e.g. "screen capture (gdigrab)". */
    String name();

    /** Why recording cannot work right now (missing program, unsupported desktop), or empty when it can. */
    Optional<String> unavailableReason();

    /**
     * Records for {@code seconds} into {@code output}, blocking until the file is written. Interrupting the calling
     * thread stops the recording; the partial file may then be left behind for the caller to delete.
     */
    void record(Path output, double seconds, Consumer<String> progress) throws VideoExportException;
}
