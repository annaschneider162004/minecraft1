package com.annaschneider.minecraft1.video.render;

import java.nio.file.Path;

/** Narration for one scene: a WAV file (or {@code null} for silence) placed at the start of the scene. */
public record SceneAudio(int sceneIndex, Path narration, double sceneSeconds) {
}
