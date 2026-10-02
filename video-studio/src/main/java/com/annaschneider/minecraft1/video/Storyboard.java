package com.annaschneider.minecraft1.video;

import java.util.List;
import java.util.Locale;

/**
 * The story generated from a prompt: a title and ordered scenes with narration.
 *
 * @param language  BCP-47-like language code of the narration ("en", "vi")
 * @param generator which generator wrote the narration ("template" or e.g. "ollama:llama3.2")
 */
public record Storyboard(String title, String prompt, String language, String generator, List<Scene> scenes) {
    public Storyboard {
        title = title == null ? "" : title;
        prompt = prompt == null ? "" : prompt;
        language = language == null || language.isBlank() ? "en" : language;
        generator = generator == null ? "" : generator;
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
        if (scenes.isEmpty()) {
            throw new IllegalArgumentException("A storyboard needs at least one scene");
        }
    }

    public double totalSeconds() {
        return scenes.stream().mapToDouble(Scene::durationSeconds).sum();
    }

    public Storyboard withScenes(List<Scene> newScenes) {
        return new Storyboard(title, prompt, language, generator, newScenes);
    }

    /** Human-readable script, also saved next to the exported video. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append(title).append('\n');
        text.append(String.format(Locale.ROOT, "%d scenes, about %.0f s - written by %s%n", scenes.size(), totalSeconds(), generator));
        for (Scene scene : scenes) {
            text.append('\n').append(String.format(Locale.ROOT, "%d. %s [%s, %.1f s, build %.0f%%%s]%n", scene.index() + 1,
                scene.title(), scene.kind().name().toLowerCase(Locale.ROOT), scene.durationSeconds(),
                scene.fromProgress() * 100, scene.isTimelapse() ? String.format(Locale.ROOT, "-%.0f%%", scene.toProgress() * 100) : ""));
            if (!scene.narration().isBlank()) {
                text.append("   ").append(scene.narration()).append('\n');
            }
        }
        return text.toString();
    }
}
