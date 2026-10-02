package com.annaschneider.minecraft1.video.voice;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Result of scanning the voices folder: the usable voices plus one message per rejected file. */
public record VoiceDiscovery(Path folder, List<VoicePack> voices, List<String> problems) {
    public VoiceDiscovery {
        voices = List.copyOf(voices);
        problems = List.copyOf(problems);
    }

    public Optional<VoicePack> find(String id) {
        return voices.stream().filter(v -> v.id().equals(id)).findFirst();
    }
}
