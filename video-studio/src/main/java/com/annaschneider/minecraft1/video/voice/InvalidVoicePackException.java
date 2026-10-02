package com.annaschneider.minecraft1.video.voice;

/** A file in the voices folder is not a usable voice pack; the message says why. */
public class InvalidVoicePackException extends Exception {
    public InvalidVoicePackException(String message) {
        super(message);
    }
}
