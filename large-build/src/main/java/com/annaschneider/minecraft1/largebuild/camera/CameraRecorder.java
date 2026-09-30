package com.annaschneider.minecraft1.largebuild.camera;

import java.util.UUID;

/**
 * Plays a {@link CameraPath} for a viewer. Implementations are platform-specific (e.g. a server-driven spectator
 * camera, or a client-side renderer/replay integration living in client-only code) and are not part of this module.
 */
public interface CameraRecorder {
    void play(UUID viewer, CameraPath path);

    void stop(UUID viewer);

    boolean isPlaying(UUID viewer);
}
