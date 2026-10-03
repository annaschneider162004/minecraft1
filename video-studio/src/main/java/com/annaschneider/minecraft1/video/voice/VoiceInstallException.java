package com.annaschneider.minecraft1.video.voice;

/** A catalog voice could not be installed; nothing was left in the voices folder. The message is meant for the user. */
public class VoiceInstallException extends Exception {
    public VoiceInstallException(String message) {
        super(message);
    }

    public VoiceInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
