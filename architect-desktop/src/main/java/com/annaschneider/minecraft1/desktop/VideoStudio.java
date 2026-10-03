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
import com.annaschneider.minecraft1.video.voice.VoiceCatalog;
import com.annaschneider.minecraft1.video.voice.VoiceCatalogSource;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoiceInstaller;
import com.annaschneider.minecraft1.video.voice.VoicePackRegistry;
import com.annaschneider.minecraft1.video.voice.ClonedVoiceProfiles;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.XttsTtsEngine;
import com.annaschneider.minecraft1.video.voice.LocalVoicePackImporter;
import com.annaschneider.minecraft1.video.voice.VoiceInstallException;

import java.nio.file.InvalidPathException;
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
        return new Narrator(List.of(new PiperTtsEngine(piper().orElse(null), runner), cloningEngine()),
            settings.narrationAudioOptions(), this::ffmpeg);
    }

    VoicePack importVoicePack(Path model, Path config, String name, String description) throws VoiceInstallException {
        return new LocalVoicePackImporter().importPack(model, config, settings.voicesFolder(), name, description);
    }

    XttsTtsEngine cloningEngine() {
        Path executable = locator.find(settings.cloningPath(), XttsTtsEngine.ENV_VARIABLE, "tts", List.of()).orElse(null);
        Path model = null;
        try {
            if (!settings.cloningModelFolder().isBlank()) {
                model = Path.of(settings.cloningModelFolder());
            }
        } catch (InvalidPathException ignored) {
            // An invalid saved path is reported as an unavailable engine.
        }
        return new XttsTtsEngine(executable, model, runner);
    }

    ClonedVoiceProfiles clonedVoices() {
        return new ClonedVoiceProfiles(cloningEngine());
    }

    VoiceDiscovery discoverVoices() {
        VoiceDiscovery packs = new VoicePackRegistry(java.util.Set.of(PiperTtsEngine.ID)).discover(settings.voicesFolder());
        VoiceDiscovery clones = clonedVoices().discover(settings.voicesFolder());
        List<VoicePack> voices = new ArrayList<>(packs.voices());
        voices.addAll(clones.voices());
        List<String> problems = new ArrayList<>(packs.problems());
        problems.addAll(clones.problems());
        return new VoiceDiscovery(settings.voicesFolder(), voices, problems);
    }

    /**
     * The unified voice catalog: the installed voices plus the verified downloadable entries bundled with the app,
     * multi-speaker models expanded per speaker. Without catalog data only the installed voices are listed.
     */
    VoiceCatalog voiceCatalog(VoiceDiscovery installed) {
        return VoiceCatalog.build(installed, VoiceCatalogSource.bundled());
    }

    /** Installs a verified catalog voice; only called when the user clicks Install voice. */
    VoiceInstaller voiceInstaller() {
        return VoiceInstaller.https();
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
        lines.add(ffmpeg().map(tool -> "FFmpeg: " + tool.executable()).orElse("FFmpeg: KHÔNG TÌM THẤY — cài FFmpeg hoặc chọn đường dẫn trong Công cụ."));
        lines.add(piper().map(path -> "Bộ đọc Piper: " + path).orElse("Bộ đọc Piper: KHÔNG TÌM THẤY — cài Piper hoặc chọn đường dẫn trong Công cụ."));
        lines.add(cloningEngine().unavailableReason().map(reason -> "Nhân bản giọng chưa sẵn sàng: " + reason)
            .orElse("Nhân bản giọng: XTTS v2 cục bộ (chỉ tiếng Anh)"));
        FootageRecorder recorder = recorder();
        lines.add(recorder.unavailableReason().map(reason -> "Ghi màn hình: CHƯA SẴN SÀNG — " + reason)
            .orElse("Ghi màn hình: " + recorder.name()));
        lines.add("Thư mục giọng: " + settings.voicesFolder() + " (" + voices.voices().size() + " giọng)");
        voices.problems().forEach(problem -> lines.add("  " + problem));
        lines.add(settings.useLocalAi() ? "Viết kịch bản: AI cục bộ " + settings.ollamaModel() + " tại " + settings.ollamaUrl()
            + " (dùng mẫu có sẵn nếu AI không chạy)" : "Viết kịch bản: mẫu có sẵn (ngoại tuyến)");
        lines.add("Thư mục đầu ra: " + settings.outputFolder());
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
