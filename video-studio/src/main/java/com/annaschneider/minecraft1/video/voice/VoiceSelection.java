package com.annaschneider.minecraft1.video.voice;

import java.util.Objects;

/**
 * The exact voice to speak with: an installed {@link VoicePack} plus, for multi-speaker models, the chosen speaker.
 * Preview, narration-only and export all receive the same selection, so they always use the same voice.
 *
 * @param id      stable catalog id: the pack id for single-speaker voices and cloned profiles, or
 *                {@code <model id>#speaker-<index>} for one speaker of a multi-speaker model
 * @param voice   the installed voice files
 * @param speaker the chosen speaker, or {@code null} for a single-speaker voice
 */
public record VoiceSelection(String id, VoicePack voice, VoiceSpeaker speaker) {
    public static final String SPEAKER_SEPARATOR = "#speaker-";

    public VoiceSelection {
        Objects.requireNonNull(voice, "voice");
        String expected = speaker == null ? voice.id() : speakerId(voice.id(), speaker.index());
        if (id == null) {
            id = expected;
        } else if (!id.equals(expected)) {
            throw new IllegalArgumentException("voice id '" + id + "' does not match '" + expected + "'");
        }
    }

    /** A single-speaker voice (installed pack or cloned profile), exactly as before the catalog existed. */
    public static VoiceSelection of(VoicePack voice) {
        return voice == null ? null : new VoiceSelection(null, voice, null);
    }

    /** One speaker of a multi-speaker model. */
    public static VoiceSelection of(VoicePack voice, VoiceSpeaker speaker) {
        return new VoiceSelection(null, voice, speaker);
    }

    /** Stable id of one speaker: model id + speaker index, never the display name. */
    public static String speakerId(String modelId, int speakerIndex) {
        return modelId + SPEAKER_SEPARATOR + speakerIndex;
    }

    public String name() {
        return speaker == null ? voice.name() : voice.name() + " - " + speaker.name();
    }

    public String language() {
        return voice.language();
    }

    public String engine() {
        return voice.engine();
    }

    public String storyLanguage() {
        return voice.storyLanguage();
    }

    /** Resolved voice for logs and status lines, e.g. {@code Arctic medium - awb (speaker 0 of en_US-arctic-medium, en_US, piper)}. */
    public String describe() {
        String where = speaker == null ? voice.id() : "speaker " + speaker.index() + " of " + voice.id();
        return name() + " (" + where + ", " + voice.language() + ", " + voice.engine() + ")";
    }
}
