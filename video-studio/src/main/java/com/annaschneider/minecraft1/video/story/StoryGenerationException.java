package com.annaschneider.minecraft1.video.story;

/** A story generator could not produce a story (e.g. the local AI backend is not running). */
public class StoryGenerationException extends Exception {
    public StoryGenerationException(String message) {
        super(message);
    }

    public StoryGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
