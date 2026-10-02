package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.PiperTtsEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoPipelineTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T08:30:00Z"), ZoneOffset.UTC);

    @Test
    void exportsANarratedVideoAndCleansUp(@TempDir Path dir) throws Exception {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        tts.secondsPerWord = 1; // long narration: scenes must grow to fit it
        Path temp = dir.resolve("tmp");
        VideoPipeline pipeline = pipeline(ffmpeg, tts, temp);
        Path footage = Files.writeString(dir.resolve("replay.mp4"), "video");

        Storyboard story = pipeline.writeStory(new StoryRequest("30 second video of a sky palace", null, null, null)).storyboard();
        List<String> progress = new ArrayList<>();
        ExportResult result = pipeline.export(new ExportRequest(story, BuildContext.empty(), List.of(footage),
            TestSupport.voice(dir), dir.resolve("out"), "Sky Palace!", null, false), progress::add);

        assertEquals(dir.resolve("out/sky-palace-20261002-083000.mp4"), result.video());
        assertTrue(Files.isRegularFile(result.video()));
        assertTrue(result.narrated());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertTrue(Files.readString(result.subtitles()).contains("-->"));
        assertTrue(Files.readString(result.script()).contains("Opening"));
        assertEquals(story.scenes().size(), tts.spoken.size());
        try (Stream<Path> left = Files.list(temp)) {
            assertEquals(0, left.count(), "temporary files must be deleted");
        }

        List<List<String>> renders = ffmpeg.renderCommands();
        long parts = renders.stream().filter(c -> c.get(c.size() - 1).matches(".*part-\\d{3}\\.mp4")).count();
        assertTrue(parts >= story.scenes().size());
        assertTrue(renders.stream().anyMatch(c -> c.contains("concat") && c.get(c.size() - 1).endsWith("video-only.mp4")));
        List<String> mux = renders.get(renders.size() - 1);
        assertTrue(mux.contains("aac") && mux.contains("-shortest"), mux.toString());
        assertTrue(renders.stream().anyMatch(c -> String.join(" ", c).contains("setpts=(PTS-STARTPTS)*")));
        assertTrue(renders.stream().anyMatch(c -> String.join(" ", c).contains("fade=t=in")));
        assertTrue(progress.stream().anyMatch(p -> p.startsWith("Generating narration")));
    }

    @Test
    void missingVoiceBackendStillExportsWithoutNarration(@TempDir Path dir) throws Exception {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        tts.unavailable = PiperTtsEngine.MISSING_MESSAGE;
        VideoPipeline pipeline = pipeline(ffmpeg, tts, dir.resolve("tmp"));
        Storyboard story = pipeline.writeStory(new StoryRequest("castle", null, 20.0, null)).storyboard();
        ExportResult result = pipeline.export(new ExportRequest(story, null, List.of(dir.resolve("missing.mp4")),
            TestSupport.voice(dir), dir.resolve("out"), null, null, false), message -> { });

        assertFalse(result.narrated());
        assertTrue(Files.isRegularFile(result.video()));
        assertTrue(result.video().getFileName().toString().startsWith("the-making-of-castle-"), result.video().toString());
        String warnings = String.join("\n", result.warnings());
        assertTrue(warnings.contains("Footage not found, skipped"), warnings);
        assertTrue(warnings.contains("title cards"), warnings);
        assertTrue(warnings.contains("Narration skipped: Piper (local text-to-speech) was not found"), warnings);
        assertTrue(ffmpeg.renderCommands().stream().noneMatch(c -> c.contains("aac")));
        assertTrue(ffmpeg.renderCommands().stream().anyMatch(c -> String.join(" ", c).contains("color=c=")));
    }

    @Test
    void missingFfmpegFailsWithInstructions(@TempDir Path dir) {
        VideoPipeline pipeline = new VideoPipeline(StoryService.templatesOnly(), new Narrator(List.of()), Optional::empty);
        Storyboard story = pipeline.writeStory(new StoryRequest("castle", null, null, null)).storyboard();
        VideoExportException error = assertThrows(VideoExportException.class, () -> pipeline.export(
            new ExportRequest(story, null, List.of(), null, dir, "x", null, false), message -> { }));
        assertTrue(error.getMessage().contains("FFmpeg was not found"));
        assertTrue(error.getMessage().contains("https://ffmpeg.org"));
    }

    @Test
    void ffmpegErrorsAreReportedAndTempFilesRemoved(@TempDir Path dir) throws IOException {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        ffmpeg.failRendering = true;
        Path temp = dir.resolve("tmp");
        VideoPipeline pipeline = pipeline(ffmpeg, new TestSupport.FakeTts(), temp);
        Storyboard story = pipeline.writeStory(new StoryRequest("castle", null, null, null)).storyboard();
        VideoExportException error = assertThrows(VideoExportException.class, () -> pipeline.export(
            new ExportRequest(story, null, List.of(), null, dir.resolve("out"), "x", null, false), message -> { }));
        assertTrue(error.getMessage().contains("Unknown encoder 'libx264'"), error.getMessage());
        try (Stream<Path> left = Files.list(temp)) {
            assertEquals(0, left.count());
        }
    }

    @Test
    void scenesGrowToFitNarrationAndFileNamesAreSafe(@TempDir Path dir) {
        Storyboard story = new Storyboard("t", "p", "en", "template", List.of(
            new Scene(0, SceneKind.INTRO, "a", "x", 0, 0, 5), new Scene(1, SceneKind.FINALE, "b", "y", 1, 1, 5)));
        Storyboard fitted = VideoPipeline.fitToNarration(story, Map.of(1, new com.annaschneider.minecraft1.video.voice.NarrationClip(1, dir, 7.25)));
        assertEquals(5, fitted.scenes().get(0).durationSeconds());
        assertEquals(8.1, fitted.scenes().get(1).durationSeconds(), 1e-9);
        assertEquals("lau-dai-trang", VideoPipeline.fileStem("L\u00e2u \u0111\u00e0i tr\u1eafng!", null));
        assertEquals("build-video", VideoPipeline.fileStem("../..", "***"));
        assertEquals("fallback", VideoPipeline.fileStem(" ", "Fallback"));
        assertNotNull(VideoPipeline.fileStem("x".repeat(100), null));
        assertEquals(40, VideoPipeline.fileStem("x".repeat(100), null).length());
    }

    @Test
    void locatesProgramsFromSettingsEnvironmentOrPath(@TempDir Path dir) throws IOException {
        Path bin = Files.createDirectories(dir.resolve("bin"));
        Path tool = Files.writeString(bin.resolve("ffmpeg"), "");
        tool.toFile().setExecutable(true);
        Path other = Files.createDirectories(dir.resolve("other"));
        Path env = Files.writeString(other.resolve("ffmpeg"), "");
        env.toFile().setExecutable(true);
        ExecutableLocator locator = new ExecutableLocator(bin.toString(), false, name -> name.equals("ARCHITECT_FFMPEG") ? env.toString() : null);
        assertEquals(env.toAbsolutePath(), locator.find("", "ARCHITECT_FFMPEG", "ffmpeg", List.of()).orElseThrow());
        assertEquals(tool.toAbsolutePath(), locator.find(bin.toString(), "ARCHITECT_FFMPEG", "ffmpeg", List.of()).orElseThrow());
        assertEquals(tool.toAbsolutePath(), locator.find("", null, "ffmpeg", List.of()).orElseThrow());
        assertTrue(locator.find(dir.resolve("nope.exe").toString(), "ARCHITECT_FFMPEG", "ffmpeg", List.of()).isEmpty(),
            "a wrong explicit path must not silently fall back");
        assertTrue(new ExecutableLocator("", false, name -> null).find(null, "X", "ffmpeg", List.of()).isEmpty());
        assertTrue(FfmpegTool.locate(locator, "", List.of(), new TestSupport.FakeFfmpeg()).isPresent());
    }

    private static VideoPipeline pipeline(TestSupport.FakeFfmpeg ffmpeg, TestSupport.FakeTts tts, Path temp) {
        return new VideoPipeline(StoryService.templatesOnly(), new Narrator(List.of(tts)),
            () -> Optional.of(new FfmpegTool(Path.of("ffmpeg"), ffmpeg)), CLOCK, temp);
    }
}
