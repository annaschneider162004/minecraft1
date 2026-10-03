package com.annaschneider.minecraft1.video.voice;

/**
 * One speaker of a multi-speaker model.
 *
 * @param index speaker number inside the model (Piper {@code --speaker}); fixed when the model was trained
 * @param name  speaker name from the model's {@code speaker_id_map} (e.g. {@code awb}, {@code p239}), never empty
 */
public record VoiceSpeaker(int index, String name) {
    public VoiceSpeaker {
        if (index < 0) {
            throw new IllegalArgumentException("speaker index must not be negative: " + index);
        }
        name = name == null || name.isBlank() ? "Speaker " + index : name.strip();
    }
}
