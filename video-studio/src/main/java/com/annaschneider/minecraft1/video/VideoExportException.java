package com.annaschneider.minecraft1.video;

/** A stage of the video pipeline failed; the message is meant for the user. */
public class VideoExportException extends Exception {
    public VideoExportException(String message) {
        super(message);
    }

    public VideoExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
