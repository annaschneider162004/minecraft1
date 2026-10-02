package com.annaschneider.minecraft1.video.story;

import com.annaschneider.minecraft1.video.Storyboard;

import java.util.List;

/** A generated storyboard plus notes for the user (e.g. "local AI unavailable, used templates"). */
public record StoryResult(Storyboard storyboard, List<String> warnings) {
    public StoryResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
