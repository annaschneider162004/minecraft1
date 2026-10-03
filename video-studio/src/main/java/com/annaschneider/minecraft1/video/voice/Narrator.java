package com.annaschneider.minecraft1.video.voice;

import com.annaschneider.minecraft1.video.Scene;
import com.annaschneider.minecraft1.video.Storyboard;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Narration stage: speaks every scene of a storyboard with the chosen voice, one WAV per scene. */
public final class Narrator {
    private final Map<String, TtsEngine> engines = new LinkedHashMap<>();

    public Narrator(List<TtsEngine> engines) {
        for (TtsEngine engine : engines) {
            this.engines.put(engine.id(), engine);
        }
    }

    public Set<String> supportedEngines() {
        return Set.copyOf(engines.keySet());
    }

    /** Why {@code voice} cannot be spoken right now, or empty when it can. */
    public Optional<String> unavailableReason(VoicePack voice) {
        TtsEngine engine = engines.get(voice.engine());
        if (engine == null) {
            return Optional.of("Voice '" + voice.name() + "' needs the '" + voice.engine() + "' engine, which this app does not have.");
        }
        String language = voice.storyLanguage();
        if (!Set.of("en", "vi").contains(language)) {
            return Optional.of("Unsupported narration language '" + voice.language() + "' (supported: en, vi).");
        }
        if (XttsTtsEngine.ID.equals(voice.engine()) && !"en".equals(language)) {
            return Optional.of("XTTS voice cloning supports English narration only.");
        }
        return engine.unavailableReason();
    }

    public List<NarrationClip> narrate(Storyboard storyboard, VoicePack voice, Path folder, Consumer<String> progress)
        throws NarrationException {
        TtsEngine engine = engine(voice);
        String language = storyboard.language().toLowerCase(Locale.ROOT).split("[_-]", 2)[0];
        if (!Set.of("en", "vi").contains(language)) {
            throw new NarrationException("Unsupported storyboard language '" + storyboard.language() + "' (supported: en, vi).");
        }
        if (!language.equals(voice.storyLanguage())) {
            throw new NarrationException("Storyboard language '" + storyboard.language() + "' does not match voice language '"
                + voice.language() + "'.");
        }
        List<NarrationClip> clips = new ArrayList<>();
        for (Scene scene : storyboard.scenes()) {
            if (scene.narration().isBlank()) {
                continue;
            }
            progress.accept(String.format(Locale.ROOT, "Speaking scene %d of %d with %s...", scene.index() + 1,
                storyboard.scenes().size(), voice.name()));
            Path wav = folder.resolve(String.format(Locale.ROOT, "narration-scene-%02d.wav", scene.index() + 1));
            engine.synthesize(voice, scene.narration(), wav);
            clips.add(new NarrationClip(scene.index(), wav, WavInfo.seconds(wav)));
        }
        return clips;
    }

    /** Speaks a short sample so the user can hear the voice. */
    public NarrationClip preview(VoicePack voice, String text, Path wav) throws NarrationException {
        TtsEngine engine = engine(voice);
        String sample = text == null || text.isBlank() ? previewText(voice.storyLanguage()) : text;
        engine.synthesize(voice, sample, wav);
        return new NarrationClip(-1, wav, WavInfo.seconds(wav));
    }

    public static String previewText(String language) {
        if (!Set.of("en", "vi").contains(language)) {
            throw new IllegalArgumentException("Unsupported preview language '" + language + "'");
        }
        return "vi".equals(language)
            ? "Xin ch\u00e0o! \u0110\u00e2y l\u00e0 gi\u1ecdng \u0111\u1ecdc cho video x\u00e2y d\u1ef1ng Minecraft c\u1ee7a b\u1ea1n."
            : "Hello! This is the voice that will narrate your Minecraft build video.";
    }

    private TtsEngine engine(VoicePack voice) throws NarrationException {
        Optional<String> problem = unavailableReason(voice);
        if (problem.isPresent()) {
            throw new NarrationException(problem.get());
        }
        return engines.get(voice.engine());
    }
}
