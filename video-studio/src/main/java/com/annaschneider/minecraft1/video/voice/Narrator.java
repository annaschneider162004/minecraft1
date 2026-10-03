package com.annaschneider.minecraft1.video.voice;

import com.annaschneider.minecraft1.video.Scene;
import com.annaschneider.minecraft1.video.Storyboard;
import com.annaschneider.minecraft1.video.render.FfmpegTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Narration stage: speaks every scene of a storyboard with the chosen voice, one WAV per scene. The voice is a
 * {@link VoiceSelection}, so a speaker of a multi-speaker model is used exactly as selected; when the engine cannot
 * speak it, a clear error is raised instead of falling back to another voice.
 */
public final class Narrator {
    private final Map<String, TtsEngine> engines = new LinkedHashMap<>();
    private final NarrationAudioProcessor audioProcessor;

    public Narrator(List<TtsEngine> engines) {
        this(engines, NarrationAudioOptions.defaults(), Optional::empty);
    }

    public Narrator(List<TtsEngine> engines, NarrationAudioOptions options,
                    Supplier<Optional<FfmpegTool>> ffmpeg) {
        this.audioProcessor = new NarrationAudioProcessor(options, ffmpeg);
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
            return Optional.of("Giọng đọc '" + selection.name() + "' cần bộ máy tổng hợp ('" + selection.engine()
                + "' engine), nhưng ứng dụng chưa có bộ máy này.");
        }
        Optional<String> missing = engine.unavailableReason();
        if (missing.isPresent()) {
            return missing;
        }
        Optional<String> unsupported = engine.unsupportedReason(selection);
        return unsupported.isPresent() ? unsupported : audioProcessor.unavailableReason();
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
            progress.accept(String.format(Locale.ROOT, "Đang đọc cảnh %d/%d bằng giọng %s...", scene.index() + 1,
                storyboard.scenes().size(), selection.name()));
            Path wav = folder.resolve(String.format(Locale.ROOT, "narration-scene-%02d.wav", scene.index() + 1));
            engine.synthesize(selection, scene.narration(), wav);
            audioProcessor.process(wav);
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
        audioProcessor.process(wav);
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
