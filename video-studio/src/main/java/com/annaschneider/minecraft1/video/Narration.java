package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.NarrationClip;

import java.util.Map;

/**
 * Narration made for a story.
 *
 * @param storyboard the story with scene lengths grown to fit the spoken narration
 * @param clips      spoken narration by scene index
 */
public record Narration(Storyboard storyboard, Map<Integer, NarrationClip> clips) {
    public Narration {
        clips = Map.copyOf(clips);
    }
}
