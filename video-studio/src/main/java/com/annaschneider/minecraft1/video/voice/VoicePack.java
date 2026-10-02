package com.annaschneider.minecraft1.video.voice;

import java.nio.file.Path;

/**
 * A validated voice found in the voices folder.
 *
 * @param id         model identifier = model file name without extension (e.g. {@code vi_VN-vais1000-medium})
 * @param name       display name
 * @param language   language code such as {@code vi_VN} or {@code en_US}
 * @param engine     TTS engine that speaks this voice (currently {@code piper})
 * @param model      model file ({@code .onnx})
 * @param config     engine config file ({@code .onnx.json})
 * @param sampleRate output sample rate in Hz
 */
public record VoicePack(String id, String name, String language, String engine, Path model, Path config, int sampleRate,
                        String description) {
    /** Short language for story text: "vi" or "en". */
    public String storyLanguage() {
        return language.toLowerCase(java.util.Locale.ROOT).startsWith("vi") ? "vi" : "en";
    }

    public String label() {
        return name + " (" + language + ")";
    }
}
