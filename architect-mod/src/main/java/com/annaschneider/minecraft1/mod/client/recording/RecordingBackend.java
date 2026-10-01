package com.annaschneider.minecraft1.mod.client.recording;

import com.annaschneider.minecraft1.link.RecordingStatus;

/**
 * Interface isolating recording providers (e.g. ReplayMod) from the rest of the mod.
 */
public interface RecordingBackend {
    boolean isAvailable();

    String name();

    RecordingStatus start();

    RecordingStatus stop();

    RecordingStatus status();
}
