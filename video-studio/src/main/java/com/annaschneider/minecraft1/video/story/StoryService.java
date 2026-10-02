package com.annaschneider.minecraft1.video.story;

import java.util.ArrayList;
import java.util.List;

/**
 * Story stage of the pipeline: tries the optional AI writer and falls back to the deterministic templates whenever it
 * is missing or fails, so a story is always produced.
 */
public final class StoryService {
    private final TemplateStoryGenerator templates;
    private final StoryGenerator ai;

    /** @param ai optional AI generator, {@code null} to always use templates */
    public StoryService(TemplateStoryGenerator templates, StoryGenerator ai) {
        this.templates = templates;
        this.ai = ai;
    }

    public static StoryService templatesOnly() {
        return new StoryService(new TemplateStoryGenerator(), null);
    }

    public StoryResult generate(StoryRequest request) {
        List<String> warnings = new ArrayList<>();
        if (request.prompt().isBlank()) {
            warnings.add("No prompt given - wrote a general build story.");
        }
        if (ai != null) {
            try {
                return new StoryResult(ai.generate(request), warnings);
            } catch (StoryGenerationException | RuntimeException ex) {
                warnings.add("Local AI unavailable (" + ex.getMessage() + ") - used the built-in story templates instead.");
            }
        }
        return new StoryResult(templates.generate(request), warnings);
    }
}
