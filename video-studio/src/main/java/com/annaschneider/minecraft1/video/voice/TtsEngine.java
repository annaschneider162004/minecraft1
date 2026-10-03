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

    /** Whether this engine can speak one chosen speaker of a multi-speaker model. */
    default boolean supportsSpeakers() {
        return false;
    }

    /**
     * Why this engine cannot speak exactly {@code selection} (unknown speaker, unsupported language, ...), or empty
     * when it can. Engines never fall back to another voice or speaker.
     */
    default Optional<String> unsupportedReason(VoiceSelection selection) {
        if (selection.speaker() != null && !supportsSpeakers()) {
            return Optional.of("The '" + id() + "' engine cannot choose a speaker, so " + selection.describe()
                + " cannot be spoken.");
        }
        return Optional.empty();
    }

    /** Speaks {@code text} with exactly the selected voice and speaker into the WAV file {@code output}. */
    default void synthesize(VoiceSelection selection, String text, Path output) throws NarrationException {
        Optional<String> problem = unsupportedReason(selection);
        if (problem.isPresent()) {
            throw new NarrationException(problem.get());
        }
        if (selection.speaker() != null) {
            throw new NarrationException("The '" + id() + "' engine does not implement speaker selection.");
        }
        synthesize(selection.voice(), text, output);
    }
}
