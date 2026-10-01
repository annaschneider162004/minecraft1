package com.annaschneider.minecraft1.link;

/**
 * Status of the in-game recording backend (e.g. ReplayMod or fallback).
 *
 * @param available  whether a recording provider is present and ready
 * @param recording  whether recording is currently in progress
 * @param state      state identifier such as "IDLE", "RECORDING", "STOPPED", "UNAVAILABLE"
 * @param backend    provider name such as "ReplayMod", "Simulated", or "None"
 * @param outputPath path or file name of the saved recording/replay, or null
 * @param message    human-readable status or error description
 */
public record RecordingStatus(
    boolean available,
    boolean recording,
    String state,
    String backend,
    String outputPath,
    String message
) {
    public static RecordingStatus unavailable(String reason) {
        return new RecordingStatus(false, false, "UNAVAILABLE", "None", null, reason);
    }

    public static RecordingStatus idle(String backend, String message) {
        return new RecordingStatus(true, false, "IDLE", backend, null, message);
    }

    public static RecordingStatus recording(String backend, String message) {
        return new RecordingStatus(true, true, "RECORDING", backend, null, message);
    }

    public static RecordingStatus stopped(String backend, String outputPath, String message) {
        return new RecordingStatus(true, false, "STOPPED", backend, outputPath, message);
    }

    public String describe() {
        if (recording) {
            return "Recording active (" + (backend != null ? backend : "ReplayMod") + ")";
        }
        if (outputPath != null && !outputPath.isBlank()) {
            return "Recording saved: " + outputPath;
        }
        if (message != null && !message.isBlank()) {
            return message;
        }
        return available ? "Recording ready (" + backend + ")" : "Recording unavailable";
    }
}
