package com.annaschneider.minecraft1.video.voice;

import java.nio.file.Path;

/** Spoken narration of one scene. */
public record NarrationClip(int sceneIndex, Path wav, double seconds) {
}
