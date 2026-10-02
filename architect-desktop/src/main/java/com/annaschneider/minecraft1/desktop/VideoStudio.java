package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.ExecutableLocator;
import com.annaschneider.minecraft1.video.ExportFlow;
import com.annaschneider.minecraft1.video.ProcessRunner;
import com.annaschneider.minecraft1.video.SystemProcessRunner;
import com.annaschneider.minecraft1.video.VideoPipeline;
import com.annaschneider.minecraft1.video.record.FootageRecorder;
import com.annaschneider.minecraft1.video.record.ScreenRecorder;
import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.story.LocalLlmStoryGenerator;
import com.annaschneider.minecraft1.video.story.OllamaClient;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.story.TemplateStoryGenerator;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.PiperTtsEngine;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoicePackRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wires the video pipeline from the saved settings: finds FFmpeg and Piper, discovers voice packs and adds the local
 * AI writer when enabled. Rebuilt whenever the settings change.
 */
final class VideoStudio {
    private final VideoStudioSettings settings;
    private final ExecutableLocator locator;
    private final ProcessRunner runner;

    VideoStudio(VideoStudioSettings settings) {
        this(settings, new ExecutableLocator(), new SystemProcessRunner());
    }

    VideoStudio(VideoStudioSettings settings, ExecutableLocator locator, ProcessRunner runner) {
        this.settings = settings;
        this.locator = locator;
        this.runner = runner;
    }

    VideoStudioSettings settings() {
        return settings;
    }

    Optional<FfmpegTool> ffmpeg() {
        Path tools = settings.voicesFolder().resolveSibling("tools");
        return FfmpegTool.locate(locator, settings.ffmpegPath(), List.of(tools, tools.resolve("ffmpeg"), tools.resolve("ffmpeg").resolve("bin")), runner);
    }

    Optional<Path> piper() {
        Path voices = settings.voicesFolder();
        Path tools = voices.resolveSibling("tools");
        return locator.find(settings.piperPath(), PiperTtsEngine.ENV_VARIABLE, "piper",
            List.of(voices.resolve("piper"), voices.resolveSibling("piper"), tools, tools.resolve("piper")));
    }

    Narrator narrator() {
        return new Narrator(List.of(new PiperTtsEngine(piper().orElse(null), runner)));
    }

    VoiceDiscovery discoverVoices() {
        return new VoicePackRegistry(narrator().supportedEngines()).discover(settings.voicesFolder());
    }

    /** @throws IllegalArgumentException when local AI is enabled with a non-local address */
    StoryService stories() {
        TemplateStoryGenerator templates = new TemplateStoryGenerator();
        if (!settings.useLocalAi()) {
            return new StoryService(templates, null);
        }
        return new StoryService(templates, new LocalLlmStoryGenerator(new OllamaClient(settings.ollamaUrl(), settings.ollamaModel()), templates));
    }

    VideoPipeline pipeline() {
        return new VideoPipeline(stories(), narrator(), this::ffmpeg);
    }

    /** Records the screen with FFmpeg for the Record only / Auto-export modes. */
    FootageRecorder recorder() {
        return new ScreenRecorder(this::ffmpeg);
    }

    /** Record only / Narrate only / Auto-export when both complete. */
    ExportFlow flow() {
        return new ExportFlow(pipeline(), recorder());
    }

    /** One line per optional dependency, for the log and the Tools dialog. */
    List<String> diagnostics(VoiceDiscovery voices) {
        List<String> lines = new ArrayList<>();
        lines.add(ffmpeg().map(tool -> "FFmpeg: " + tool.executable()).orElse("FFmpeg: NOT FOUND - " + FfmpegTool.MISSING_MESSAGE));
        lines.add(piper().map(path -> "Piper voice engine: " + path).orElse("Piper voice engine: NOT FOUND - " + PiperTtsEngine.MISSING_MESSAGE));
        FootageRecorder recorder = recorder();
        lines.add(recorder.unavailableReason().map(reason -> "Screen recording: NOT AVAILABLE - " + reason)
            .orElse("Screen recording: " + recorder.name()));
        lines.add("Voices folder: " + settings.voicesFolder() + " (" + voices.voices().size() + " voice"
            + (voices.voices().size() == 1 ? "" : "s") + ")");
        voices.problems().forEach(problem -> lines.add("  " + problem));
        lines.add(settings.useLocalAi() ? "Story writer: local AI " + settings.ollamaModel() + " at " + settings.ollamaUrl()
            + " (built-in templates if it is not running)" : "Story writer: built-in templates (offline)");
        lines.add("Output folder: " + settings.outputFolder());
        return lines;
    }

    /** {@code <.minecraft>/replay_videos}, where ReplayMod saves rendered videos, derived from the link file location. */
    static Path replayVideosFolder(Path linkFile) {
        Path architect = linkFile == null ? null : linkFile.toAbsolutePath().getParent();
        Path config = architect == null ? null : architect.getParent();
        Path gameDir = config == null ? null : config.getParent();
        return gameDir == null ? LinkFileLocator.defaultLinkFile().getParent().getParent().getParent().resolve("replay_videos")
            : gameDir.resolve("replay_videos");
    }
}
