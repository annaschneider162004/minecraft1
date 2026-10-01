package com.annaschneider.minecraft1.mod.client.recording;

import com.annaschneider.minecraft1.link.RecordingStatus;
import com.annaschneider.minecraft1.mod.recording.RecordingChannels;

import java.util.Objects;

/**
 * Client-side manager that processes recording commands and interfaces with the recording backend.
 */
public final class ClientRecordingManager {
    private final RecordingBackend backend;

    public ClientRecordingManager() {
        this(new ReplayModBackend());
    }

    public ClientRecordingManager(RecordingBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public RecordingBackend backend() {
        return backend;
    }

    public RecordingStatus handleCommand(String command) {
        if (command == null) {
            return backend.status();
        }
        return switch (command) {
            case RecordingChannels.ACTION_START -> backend.start();
            case RecordingChannels.ACTION_STOP -> backend.stop();
            case RecordingChannels.ACTION_STATUS -> backend.status();
            default -> backend.status();
        };
    }
}
