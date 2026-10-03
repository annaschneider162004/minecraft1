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

/**
 * Narration stage: speaks every scene of a storyboard with the chosen voice, one WAV per scene. The voice is a
 * {@link VoiceSelection}, so a speaker of a multi-speaker model is used exactly as selected; when the engine cannot
 * speak it, a clear error is raised instead of falling back to another voice.
 */
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
        return unavailableReason(VoiceSelection.of(voice));
    }

    /** Why exactly {@code selection} (voice and speaker) cannot be spoken right now, or empty when it can. */
    public Optional<String> unavailableReason(VoiceSelection selection) {
        TtsEngine engine = engines.get(selection.engine());
        if (engine == null) {
            return Optional.of("Voice '" + selection.name() + "' needs the '" + selection.engine()
                + "' engine, which this app does not have.");
        }
        Optional<String> missing = engine.unavailableReason();
        return missing.isPresent() ? missing : engine.unsupportedReason(selection);
    }

    public List<NarrationClip> narrate(Storyboard storyboard, VoicePack voice, Path folder, Consumer<String> progress)
        throws NarrationException {
        return narrate(storyboard, VoiceSelection.of(voice), folder, progress);
    }

    public List<NarrationClip> narrate(Storyboard storyboard, VoiceSelection selection, Path folder, Consumer<String> progress)
        throws NarrationException {
        TtsEngine engine = engine(selection);
        List<NarrationClip> clips = new ArrayList<>();
        for (Scene scene : storyboard.scenes()) {
            if (scene.narration().isBlank()) {
                continue;
            }
            progress.accept(String.format(Locale.ROOT, "Speaking scene %d of %d with %s...", scene.index() + 1,
                storyboard.scenes().size(), selection.name()));
            Path wav = folder.resolve(String.format(Locale.ROOT, "narration-scene-%02d.wav", scene.index() + 1));
            engine.synthesize(selection, scene.narration(), wav);
            clips.add(new NarrationClip(scene.index(), wav, WavInfo.seconds(wav)));
        }
        return clips;
    }

    /** Speaks a short sample so the user can hear the voice. */
    public NarrationClip preview(VoicePack voice, String text, Path wav) throws NarrationException {
        return preview(VoiceSelection.of(voice), text, wav);
    }

    /** Speaks a short sample with exactly the selected voice and speaker. */
    public NarrationClip preview(VoiceSelection selection, String text, Path wav) throws NarrationException {
        String sample = text == null || text.isBlank() ? previewText(selection.storyLanguage()) : text;
        engine(selection).synthesize(selection, sample, wav);
        return new NarrationClip(-1, wav, WavInfo.seconds(wav));
    }

    public static String previewText(String language) {
        return "vi".equals(language)
            ? "Xin ch\u00e0o! \u0110\u00e2y l\u00e0 gi\u1ecdng \u0111\u1ecdc cho video x\u00e2y d\u1ef1ng Minecraft c\u1ee7a b\u1ea1n."
            : "Hello! This is the voice that will narrate your Minecraft build video.";
    }

    private TtsEngine engine(VoiceSelection selection) throws NarrationException {
        Optional<String> problem = unavailableReason(selection);
        if (problem.isPresent()) {
            throw new NarrationException(problem.get());
        }
        return engines.get(selection.engine());
    }
}
