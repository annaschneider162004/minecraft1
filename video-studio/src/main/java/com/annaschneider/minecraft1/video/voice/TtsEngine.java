package com.annaschneider.minecraft1.video.voice;

import java.nio.file.Path;
import java.util.Optional;

/** A local, offline text-to-speech engine. Voice packs name the engine that speaks them. */
public interface TtsEngine {
    /** Engine id used in voice pack metadata, e.g. {@code piper}. */
    String id();

    /** Why the engine cannot be used right now (e.g. not installed), or empty when it is ready. */
    Optional<String> unavailableReason();

    /** Speaks {@code text} with {@code voice} into the WAV file {@code output}. */
    void synthesize(VoicePack voice, String text, Path output) throws NarrationException;
}
