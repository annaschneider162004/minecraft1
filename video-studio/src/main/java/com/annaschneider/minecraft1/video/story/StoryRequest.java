package com.annaschneider.minecraft1.video.story;

import com.annaschneider.minecraft1.video.BuildContext;

/**
 * Input of story generation.
 *
 * @param targetSeconds wanted video length, or {@code null} to read it from the prompt (default 60 s)
 * @param language      narration language ("en", "vi"), or {@code null} to detect it from the prompt
 */
public record StoryRequest(String prompt, BuildContext context, Double targetSeconds, String language) {
    public StoryRequest {
        prompt = prompt == null ? "" : prompt.strip();
        context = context == null ? BuildContext.empty() : context;
    }
}
