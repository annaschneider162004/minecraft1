package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.voice.NarrationAudioOptions;
import com.annaschneider.minecraft1.video.voice.NarrationAudioOptions.Effect;
import com.annaschneider.minecraft1.video.voice.NarrationClip;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.WavInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NarrationAudioTest {
    @Test
    void validatesSpeedAndEffect() {
        assertEquals(new NarrationAudioOptions(1, Effect.NONE), NarrationAudioOptions.defaults());
        for (double speed : new double[] {0.5, 1, 2}) {
            assertEquals(speed, new NarrationAudioOptions(speed, Effect.SOFT_ECHO).speed());
        }
        for (double speed : new double[] {0.49, 2.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new NarrationAudioOptions(speed, Effect.NONE));
        }
        assertThrows(NullPointerException.class, () -> new NarrationAudioOptions(1, null));
        assertEquals("Tiếng vang nhẹ", Effect.SOFT_ECHO.toString());
    }

    @Test
    void defaultsNeverResolveOrRunFfmpeg(@TempDir Path dir) throws Exception {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        Narrator narrator = new Narrator(List.of(tts), NarrationAudioOptions.defaults(), () -> {
            throw new AssertionError("Default audio must not require FFmpeg");
        });
        assertTrue(narrator.unavailableReason(TestSupport.voice(dir)).isEmpty());
        assertEquals(0.8, narrator.preview(TestSupport.voice(dir), "two words", dir.resolve("preview.wav")).seconds(), 0.001);
        assertEquals(1.2, narrator.narrate(story(), TestSupport.voice(dir), dir, ignored -> { }).get(0).seconds(), 0.001);
    }

    @Test
    void previewAppliesSpeedAndEchoAndReadsProcessedDuration(@TempDir Path dir) throws Exception {
        AudioRunner runner = new AudioRunner();
        Narrator narrator = narrator(dir, runner, 2, Effect.SOFT_ECHO);
        Path wav = dir.resolve("preview ; $(not-a-command).wav");
        NarrationClip clip = narrator.preview(TestSupport.voice(dir), "one two three four", wav);
        assertEquals(-1, clip.sceneIndex());
        assertEquals(0.86, clip.seconds(), 0.001);
        assertEquals(WavInfo.seconds(wav), clip.seconds(), 1e-9);
        assertCommand(runner.commands.get(0), wav, "atempo=2.0,aecho=0.8:0.9:60:0.2");
        assertNoTemporaryOutput(dir);
    }

    @Test
    void narrationProcessesEachNonblankSceneWithSpeedAndEcho(@TempDir Path dir) throws Exception {
        AudioRunner runner = new AudioRunner();
        List<NarrationClip> clips = narrator(dir, runner, 0.5, Effect.SOFT_ECHO)
            .narrate(story(), TestSupport.voice(dir), dir, ignored -> { });
        assertEquals(2, runner.commands.size());
        assertEquals(List.of(0, 2), clips.stream().map(NarrationClip::sceneIndex).toList());
        assertEquals(2.46, clips.get(0).seconds(), 0.001);
        assertEquals(1.66, clips.get(1).seconds(), 0.001);
        for (int i = 0; i < clips.size(); i++) {
            assertCommand(runner.commands.get(i), clips.get(i).wav(), "atempo=0.5,aecho=0.8:0.9:60:0.2");
            assertEquals(WavInfo.seconds(clips.get(i).wav()), clips.get(i).seconds(), 1e-9);
        }
        assertFalse(Files.exists(dir.resolve("narration-scene-02.wav")));
        assertNoTemporaryOutput(dir);
    }

    @Test
    void supportsSpeedOnlyAndEffectOnly(@TempDir Path dir) throws Exception {
        AudioRunner runner = new AudioRunner();
        NarrationClip faster = narrator(dir, runner, 1.5, Effect.NONE)
            .preview(TestSupport.voice(dir), "one two three", dir.resolve("speed.wav"));
        assertEquals(0.8, faster.seconds(), 0.001);
        assertCommand(runner.commands.get(0), faster.wav(), "atempo=1.5");
        NarrationClip echo = narrator(dir, runner, 1, Effect.SOFT_ECHO)
            .preview(TestSupport.voice(dir), "one two three", dir.resolve("echo.wav"));
        assertEquals(1.26, echo.seconds(), 0.001);
        assertCommand(runner.commands.get(1), echo.wav(), "atempo=1.0,aecho=0.8:0.9:60:0.2");
    }

    @Test
    void missingFfmpegReportsVietnameseReasonBeforeSynthesis(@TempDir Path dir) {
        for (NarrationAudioOptions options : List.of(new NarrationAudioOptions(2, Effect.NONE),
            new NarrationAudioOptions(1, Effect.SOFT_ECHO))) {
            TestSupport.FakeTts tts = new TestSupport.FakeTts();
            Narrator narrator = new Narrator(List.of(tts), options, Optional::empty);
            String reason = narrator.unavailableReason(TestSupport.voice(dir)).orElseThrow();
            assertTrue(reason.contains("Không tìm thấy FFmpeg"));
            assertEquals(reason, assertThrows(NarrationException.class,
                () -> narrator.preview(TestSupport.voice(dir), "hi", dir.resolve("preview.wav"))).getMessage());
            assertEquals(reason, assertThrows(NarrationException.class,
                () -> narrator.narrate(story(), TestSupport.voice(dir), dir, ignored -> { })).getMessage());
            assertTrue(tts.spoken.isEmpty());
        }
    }

    @Test
    void failedProcessingPreservesOriginalAndCleansOutput(@TempDir Path dir) throws Exception {
        AudioRunner runner = new AudioRunner();
        runner.fail = true;
        Path wav = dir.resolve("preview.wav");
        NarrationException error = assertThrows(NarrationException.class,
            () -> narrator(dir, runner, 2, Effect.NONE).preview(TestSupport.voice(dir), "two words", wav));
        assertTrue(error.getMessage().contains("Không thể xử lý"));
        assertTrue(error.getMessage().contains("filter failed"));
        assertEquals(0.8, WavInfo.seconds(wav), 0.001);
        assertNoTemporaryOutput(dir);
    }

    @Test
    void invalidProcessedWavDoesNotReplaceOriginal(@TempDir Path dir) throws Exception {
        FfmpegTool tool = new FfmpegTool(dir.resolve("ffmpeg"), (command, stdin, timeout) -> {
            Files.writeString(Path.of(command.get(command.size() - 1)), "invalid WAV");
            return new ProcessRunner.Result(0, "");
        });
        Narrator narrator = new Narrator(List.of(new TestSupport.FakeTts()),
            new NarrationAudioOptions(2, Effect.NONE), () -> Optional.of(tool));
        Path wav = dir.resolve("preview.wav");
        assertThrows(NarrationException.class, () -> narrator.preview(TestSupport.voice(dir), "two words", wav));
        assertEquals(0.8, WavInfo.seconds(wav), 0.001);
        assertNoTemporaryOutput(dir);
    }

    @Test
    void interruptedProcessingRetainsInterruptAndCleansPartialOutput(@TempDir Path dir) throws Exception {
        FfmpegTool tool = new FfmpegTool(dir.resolve("ffmpeg"), (command, stdin, timeout) -> {
            TestSupport.writeWav(Path.of(command.get(command.size() - 1)), 0.2);
            throw new InterruptedException("cancelled");
        });
        Narrator narrator = new Narrator(List.of(new TestSupport.FakeTts()),
            new NarrationAudioOptions(2, Effect.SOFT_ECHO), () -> Optional.of(tool));
        Path wav = dir.resolve("preview.wav");
        try {
            NarrationException error = assertThrows(NarrationException.class,
                () -> narrator.preview(TestSupport.voice(dir), "two words", wav));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(error.getCause().getCause() instanceof InterruptedException);
            assertEquals(0.8, WavInfo.seconds(wav), 0.001);
            assertNoTemporaryOutput(dir);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void realFfmpegChangesPreviewAndNarrationDuration(@TempDir Path dir) throws Exception {
        Optional<FfmpegTool> found = FfmpegTool.locate(new ExecutableLocator(), "", List.of(), new SystemProcessRunner());
        assumeTrue(found.isPresent(), "FFmpeg not installed");
        Narrator narrator = new Narrator(List.of(new TestSupport.FakeTts()),
            new NarrationAudioOptions(2, Effect.SOFT_ECHO), () -> found);
        Path wav = dir.resolve("real-preview.wav");
        NarrationClip preview = narrator.preview(TestSupport.voice(dir), "one two three four", wav);
        assertEquals(0.86, preview.seconds(), 0.12);
        assertEquals(WavInfo.seconds(wav), preview.seconds(), 1e-9);
        List<NarrationClip> clips = narrator.narrate(story(), TestSupport.voice(dir), dir, ignored -> { });
        assertEquals(0.66, clips.get(0).seconds(), 0.12);
        assertEquals(0.46, clips.get(1).seconds(), 0.12);
        assertNoTemporaryOutput(dir);
    }

    private static Narrator narrator(Path dir, AudioRunner runner, double speed, Effect effect) {
        FfmpegTool tool = new FfmpegTool(dir.resolve("ffmpeg"), runner);
        return new Narrator(List.of(new TestSupport.FakeTts()), new NarrationAudioOptions(speed, effect),
            () -> Optional.of(tool));
    }

    private static Storyboard story() {
        return new Storyboard("t", "p", "en", "template", List.of(
            new Scene(0, SceneKind.INTRO, "a", "one two three", 0, 0, 5),
            new Scene(1, SceneKind.TIMELAPSE, "b", "", 0, 1, 5),
            new Scene(2, SceneKind.FINALE, "c", "four five", 1, 1, 5)));
    }

    private static void assertCommand(List<String> command, Path input, String filters) {
        assertEquals(input.toString(), command.get(command.indexOf("-i") + 1));
        assertEquals(filters, command.get(command.indexOf("-af") + 1));
        assertEquals("pcm_s16le", command.get(command.indexOf("-c:a") + 1));
        assertEquals("wav", command.get(command.indexOf("-f") + 1));
        assertTrue(command.contains("-nostdin"));
        Path output = Path.of(command.get(command.size() - 1));
        assertEquals(input.toAbsolutePath().getParent(), output.getParent());
        assertFalse(input.equals(output));
        assertFalse(Files.exists(output));
    }

    private static void assertNoTemporaryOutput(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("narration-audio-")));
        }
    }

    private static final class AudioRunner implements ProcessRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private boolean fail;

        @Override
        public Result run(List<String> command, String stdin, Duration timeout) throws IOException {
            commands.add(List.copyOf(command));
            assertNull(stdin);
            Path input = Path.of(command.get(command.indexOf("-i") + 1));
            Path output = Path.of(command.get(command.size() - 1));
            String filters = command.get(command.indexOf("-af") + 1);
            double speed = Double.parseDouble(filters.substring("atempo=".length()).split(",")[0]);
            try {
                TestSupport.writeWav(output, WavInfo.seconds(input) / speed + (filters.contains("aecho") ? 0.06 : 0));
            } catch (NarrationException ex) {
                throw new IOException(ex);
            }
            return new Result(fail ? 1 : 0, fail ? "filter failed" : "");
        }
    }
}
