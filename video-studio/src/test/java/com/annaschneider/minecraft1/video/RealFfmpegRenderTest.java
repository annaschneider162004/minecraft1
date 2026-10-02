package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.render.RenderOptions;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.voice.Narrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** End-to-end render with the real FFmpeg; skipped when FFmpeg is not installed. */
class RealFfmpegRenderTest {
    @Test
    void rendersANarratedMp4FromGeneratedFootage(@TempDir Path dir) throws Exception {
        SystemProcessRunner runner = new SystemProcessRunner();
        Optional<FfmpegTool> found = FfmpegTool.locate(new ExecutableLocator(), "", List.of(), runner);
        assumeTrue(found.isPresent(), "FFmpeg not installed");
        FfmpegTool ffmpeg = found.get();
        Path footage = dir.resolve("footage.mp4");
        ffmpeg.run(List.of("-f", "lavfi", "-i", "testsrc=size=320x240:rate=25:duration=12", "-c:v", "libx264",
            "-pix_fmt", "yuv420p", footage.toString()), "making test footage");
        assertEquals(12, ffmpeg.probeDuration(footage), 0.1);

        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        VideoPipeline pipeline = new VideoPipeline(StoryService.templatesOnly(), new Narrator(List.of(tts)), () -> found);
        Storyboard story = pipeline.writeStory(new StoryRequest("16 second timelapse of a tower", null, null, null)).storyboard();
        ExportResult result = pipeline.export(new ExportRequest(story, null, List.of(footage), TestSupport.voice(dir),
            dir.resolve("out"), "tower", new RenderOptions(320, 180, 15), false), message -> { });

        assertTrue(result.narrated(), result.warnings().toString());
        double length = ffmpeg.probeDuration(result.video());
        assertTrue(Math.abs(length - result.storyboard().totalSeconds()) < 1.0, "video " + length + " s vs story " + result.storyboard().totalSeconds());
        ProcessRunner.Result streams = runner.run(List.of(ffmpeg.executable().toString(), "-hide_banner", "-i",
            result.video().toString()), null, Duration.ofSeconds(30));
        assertTrue(streams.output().contains("Video: h264"), streams.output());
        assertTrue(streams.output().contains("Audio: aac"), streams.output());
    }
}
