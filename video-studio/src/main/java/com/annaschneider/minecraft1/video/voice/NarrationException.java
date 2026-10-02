package com.annaschneider.minecraft1.video.voice;

/** Narration could not be generated (engine missing, voice broken, ...); the message is meant for the user. */
public class NarrationException extends Exception {
    public NarrationException(String message) {
        super(message);
    }

    public NarrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
