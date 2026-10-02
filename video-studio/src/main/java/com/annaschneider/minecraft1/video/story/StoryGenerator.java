package com.annaschneider.minecraft1.video.story;

import com.annaschneider.minecraft1.video.Storyboard;

/** Turns a prompt plus build context into a storyboard. */
public interface StoryGenerator {
    /** Short identifier shown to the user, e.g. "template" or "ollama:llama3.2". */
    String id();

    Storyboard generate(StoryRequest request) throws StoryGenerationException;
}
