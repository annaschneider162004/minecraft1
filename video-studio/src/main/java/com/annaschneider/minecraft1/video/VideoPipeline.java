package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.render.SceneAudio;
import com.annaschneider.minecraft1.video.render.SubtitleWriter;
import com.annaschneider.minecraft1.video.render.VideoRenderer;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryResult;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.voice.NarrationClip;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.VoicePack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prompt-to-video pipeline with separate stages: story ({@link StoryService}), footage probing, narration
 * ({@link Narrator}), segment planning ({@link SegmentPlanner}) and rendering ({@link VideoRenderer}). Narration problems
 * never stop an export: the video is then rendered without voice and a warning explains why. Temporary files are kept
 * in one {@code architect-video-*} folder that is deleted afterwards.
 */
public final class VideoPipeline {
    static final double NARRATION_PADDING_SECONDS = 0.8;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final StoryService stories;
    private final Narrator narrator;
    private final Supplier<Optional<FfmpegTool>> ffmpeg;
    private final SegmentPlanner planner = new SegmentPlanner();
    private final Clock clock;
    private final Path temporaryRoot;

    public VideoPipeline(StoryService stories, Narrator narrator, Supplier<Optional<FfmpegTool>> ffmpeg) {
        this(stories, narrator, ffmpeg, Clock.systemDefaultZone(), null);
    }

    /** @param temporaryRoot where the work folder is created, {@code null} for the system temp folder */
    public VideoPipeline(StoryService stories, Narrator narrator, Supplier<Optional<FfmpegTool>> ffmpeg, Clock clock,
                         Path temporaryRoot) {
        this.stories = stories;
        this.narrator = narrator;
        this.ffmpeg = ffmpeg;
        this.clock = clock;
        this.temporaryRoot = temporaryRoot;
    }

    /** Stage 1: prompt + build context to storyboard (never fails; falls back to templates). */
    public StoryResult writeStory(StoryRequest request) {
        return stories.generate(request);
    }

    /** FFmpeg, when it is installed. */
    public Optional<FfmpegTool> ffmpeg() {
        return ffmpeg.get();
    }

    /** Why {@code voice} cannot be spoken right now (e.g. Piper missing), or empty when it can. */
    public Optional<String> narrationUnavailable(VoicePack voice) {
        return narrator.unavailableReason(voice);
    }

    /** Narration stage on its own: one WAV per scene in {@code folder}; scenes grow to fit their narration. */
    public Narration narrate(Storyboard storyboard, VoicePack voice, Path folder, Consumer<String> progress) throws NarrationException {
        Map<Integer, NarrationClip> clips = narrator.narrate(storyboard, voice, folder, progress).stream()
            .collect(Collectors.toMap(NarrationClip::sceneIndex, Function.identity()));
        return new Narration(fitToNarration(storyboard, clips), clips);
    }

    /** Stages 2-5: probe footage, narrate, plan segments, render the MP4 and write subtitles and script. */
    public ExportResult export(ExportRequest request, Consumer<String> progress) throws VideoExportException {
        return export(request, null, null, progress);
    }

    /**
     * @param narrated narration made beforehand (e.g. in parallel with the recording), or {@code null} to narrate
     *                 {@code request.voice()} here
     * @param stem     exact output file stem, or {@code null} for the base name plus a timestamp
     */
    ExportResult export(ExportRequest request, Narration narrated, String stem, Consumer<String> progress) throws VideoExportException {
        FfmpegTool tool = ffmpeg.get().orElseThrow(() -> new VideoExportException(FfmpegTool.MISSING_MESSAGE));
        List<String> warnings = new ArrayList<>();
        Path work;
        try {
            Files.createDirectories(request.outputFolder());
            work = temporaryRoot == null ? Files.createTempDirectory("architect-video-")
                : Files.createTempDirectory(Files.createDirectories(temporaryRoot), "architect-video-");
        } catch (IOException ex) {
            throw new VideoExportException("Cannot create the output folder " + request.outputFolder() + ": " + ex.getMessage(), ex);
        }
        try {
            List<FootageClip> footage = probeFootage(tool, request.footage(), warnings, progress);
            if (footage.isEmpty()) {
                warnings.add("No footage was added - the video uses plain title cards. Add a ReplayMod/OBS video to see the build.");
            }

            Storyboard storyboard = request.storyboard();
            Map<Integer, NarrationClip> narration = Map.of();
            if (narrated != null) {
                storyboard = narrated.storyboard();
                narration = narrated.clips();
            } else if (request.voice() != null) {
                progress.accept("Generating narration with " + request.voice().name() + "...");
                try {
                    narration = narrator.narrate(storyboard, request.voice(), work, progress).stream()
                        .collect(Collectors.toMap(NarrationClip::sceneIndex, Function.identity()));
                    storyboard = fitToNarration(storyboard, narration);
                } catch (NarrationException ex) {
                    warnings.add("Narration skipped: " + ex.getMessage() + " The video was exported without narration.");
                    narration = Map.of();
                }
            }

            progress.accept("Planning " + storyboard.scenes().size() + " scenes...");
            List<PlannedSegment> segments = planner.plan(storyboard, request.context(), footage);
            List<SceneAudio> audio = new ArrayList<>();
            if (!narration.isEmpty()) {
                for (Scene scene : storyboard.scenes()) {
                    NarrationClip clip = narration.get(scene.index());
                    audio.add(new SceneAudio(scene.index(), clip == null ? null : clip.wav(), scene.durationSeconds()));
                }
            }

            if (stem == null) {
                stem = stamped(request.baseName(), storyboard.title());
            }
            Path video = request.outputFolder().resolve(stem + ".mp4");
            new VideoRenderer(tool, request.options()).render(segments, audio, work, video, progress);

            Path script = request.outputFolder().resolve(stem + "-story.txt");
            Files.writeString(script, storyboard.describe(), StandardCharsets.UTF_8);
            Path subtitles = null;
            String srt = SubtitleWriter.toSrt(storyboard);
            if (!srt.isBlank()) {
                subtitles = request.outputFolder().resolve(stem + ".srt");
                Files.writeString(subtitles, srt, StandardCharsets.UTF_8);
            }
            progress.accept("Done: " + video);
            return new ExportResult(storyboard, video, subtitles, script, !narration.isEmpty(), warnings);
        } catch (IOException ex) {
            throw new VideoExportException("Could not write the export files: " + ex.getMessage(), ex);
        } finally {
            if (request.keepTemporaryFiles()) {
                progress.accept("Temporary files kept in " + work);
            } else {
                deleteRecursively(work);
            }
        }
    }

    /** Output file stem: safe name plus the current time, e.g. {@code sky-palace-20261002-083000}. */
    String stamped(String preferred, String fallback) {
        return fileStem(preferred, fallback) + "-" + LocalDateTime.now(clock).format(STAMP);
    }

    private static List<FootageClip> probeFootage(FfmpegTool tool, List<Path> files, List<String> warnings,
                                                  Consumer<String> progress) {
        List<FootageClip> clips = new ArrayList<>();
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                warnings.add("Footage not found, skipped: " + file);
                continue;
            }
            progress.accept("Reading footage " + file.getFileName() + "...");
            try {
                double seconds = tool.probeDuration(file);
                if (seconds < 0.5) {
                    warnings.add("Footage too short, skipped: " + file.getFileName());
                } else {
                    clips.add(new FootageClip(file, seconds));
                }
            } catch (VideoExportException ex) {
                warnings.add("Footage skipped: " + ex.getMessage());
            }
        }
        return clips;
    }

    /** Lengthens scenes whose narration is longer than planned, so speech is never cut off. */
    static Storyboard fitToNarration(Storyboard storyboard, Map<Integer, NarrationClip> narration) {
        List<Scene> scenes = new ArrayList<>();
        for (Scene scene : storyboard.scenes()) {
            NarrationClip clip = narration.get(scene.index());
            double needed = clip == null ? 0 : Math.ceil((clip.seconds() + NARRATION_PADDING_SECONDS) * 10) / 10.0;
            scenes.add(needed > scene.durationSeconds() ? scene.withDuration(needed) : scene);
        }
        return storyboard.withScenes(scenes);
    }

    /** Safe ASCII file name stem: "Lâu đài trắng!" becomes "lau-dai-trang". */
    public static String fileStem(String preferred, String fallback) {
        for (String candidate : new String[] {preferred, fallback}) {
            if (candidate == null) {
                continue;
            }
            String ascii = Normalizer.normalize(candidate.replace('\u0111', 'd').replace('\u0110', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
            if (ascii.length() > 40) {
                ascii = ascii.substring(0, 40).replaceAll("-+$", "");
            }
            if (!ascii.isEmpty()) {
                return ascii;
            }
        }
        return "build-video";
    }

    static void deleteRecursively(Path folder) {
        if (folder == null || !Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort: a locked temp file is left for the OS to clean up
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
