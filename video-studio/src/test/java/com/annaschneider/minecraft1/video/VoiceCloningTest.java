package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.render.RenderOptions;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.voice.ClonedVoiceProfiles;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.WavInfo;
import com.annaschneider.minecraft1.video.voice.XttsTtsEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioSystem;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VoiceCloningTest {
    @TempDir Path dir;

    @Test
    void createsReloadsPreviewsAndExportsWithTheCopiedSample() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        XttsTtsEngine engine = engine((command, stdin, timeout) -> {
            assertNull(stdin);
            assertTrue(timeout.toMinutes() >= 3);
            commands.add(command);
            TestSupport.writeWav(Path.of(command.get(command.indexOf("--out_path") + 1)), 1);
            return new ProcessRunner.Result(0, "");
        });
        Path sample = TestSupport.writeWav(dir.resolve("sample.wav"), 6);
        Path folder = dir.resolve("voices");
        List<String> states = new ArrayList<>();
        VoicePack profile = new ClonedVoiceProfiles(engine).create(folder, sample, "My voice", states::add);
        assertEquals(List.of(ClonedVoiceProfiles.CHECKING, ClonedVoiceProfiles.CREATING, ClonedVoiceProfiles.READY), states);
        assertEquals("My voice [cloned]", profile.name());
        Files.delete(sample);
        var found = new ClonedVoiceProfiles(engine).discover(folder);
        assertTrue(found.problems().isEmpty(), found.problems().toString());
        assertEquals(List.of(profile), found.voices());
        assertTrue(Files.isRegularFile(profile.model()), "the profile owns a persistent copy");
        Narrator narrator = new Narrator(List.of(engine));
        Path preview = dir.resolve("preview.wav");
        narrator.preview(profile, "Hello cloned voice", preview);
        assertEquals(24000, AudioSystem.getAudioFileFormat(preview.toFile()).getFormat().getSampleRate());
        assertEquals(22050.0 / 24000, WavInfo.seconds(preview), 0.001, "corrects the CLI's input-rate header");
        List<String> command = commands.get(1);
        assertEquals(profile.model().toString(), command.get(command.indexOf("--speaker_wav") + 1));
        assertEquals("en", command.get(command.indexOf("--language_idx") + 1));
        assertEquals("Hello cloned voice", command.get(command.indexOf("--text") + 1));
        assertFalse(command.contains("--model_name"), "never requests a model download");
        assertTrue(Files.isDirectory(Path.of(command.get(command.indexOf("--model_path") + 1))));

        Path ffmpegFile = Files.writeString(dir.resolve("ffmpeg"), "fake");
        FfmpegTool ffmpeg = new FfmpegTool(ffmpegFile, new TestSupport.FakeFfmpeg());
        VideoPipeline pipeline = new VideoPipeline(StoryService.templatesOnly(), narrator, () -> Optional.of(ffmpeg));
        Storyboard story = pipeline.writeStory(new StoryRequest("16 second tower build", null, null, "en")).storyboard();
        ExportFlow flow = new ExportFlow(pipeline, null);
        FlowResult narration = flow.run(new FlowRequest(ExportMode.NARRATE_ONLY, story, null, profile, 0,
            dir.resolve("out"), "cloned", null), new FlowListener() {
                @Override public void status(FlowStatus status) { }
                @Override public void progress(String message) { }
            });
        assertTrue(narration.ok(), String.valueOf(narration.failure()));
        assertTrue(WavInfo.seconds(narration.narration()) > 0);
        Path footage = Files.writeString(dir.resolve("footage.mp4"), "fake");
        ExportResult export = pipeline.export(new ExportRequest(story, null, List.of(footage), profile,
            dir.resolve("out"), "cloned-video", RenderOptions.hd720(), false), ignored -> { });
        assertTrue(export.narrated(), export.warnings().toString());
        assertTrue(Files.isRegularFile(export.video()));
    }

    @Test
    void rejectsWrongFormatShortLongOversizedCorruptAndTruncatedSamples() throws Exception {
        Path shortSample = TestSupport.writeWav(dir.resolve("short.wav"), 5.9);
        assertTrue(assertThrows(NarrationException.class, () -> ClonedVoiceProfiles.validateSample(shortSample))
            .getMessage().contains("6 đến 60 giây"));
        Path longSample = TestSupport.writeWav(dir.resolve("long.wav"), 60.1);
        assertThrows(NarrationException.class, () -> ClonedVoiceProfiles.validateSample(longSample));
        Path mp3 = Files.writeString(dir.resolve("sample.mp3"), "not audio");
        assertTrue(assertThrows(NarrationException.class, () -> ClonedVoiceProfiles.validateSample(mp3))
            .getMessage().contains("không được hỗ trợ"));
        Path corrupt = Files.write(dir.resolve("corrupt.wav"), new byte[100]);
        assertThrows(NarrationException.class, () -> ClonedVoiceProfiles.validateSample(corrupt));
        Path oversized = dir.resolve("huge.wav");
        try (RandomAccessFile file = new RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(ClonedVoiceProfiles.MAX_SAMPLE_BYTES + 1);
        }
        assertTrue(assertThrows(NarrationException.class, () -> ClonedVoiceProfiles.validateSample(oversized))
            .getMessage().contains("20 MB"));
        Path truncated = TestSupport.writeWav(dir.resolve("truncated.wav"), 6);
        try (RandomAccessFile file = new RandomAccessFile(truncated.toFile(), "rw")) {
            file.setLength(1000);
        }
        assertTrue(assertThrows(NarrationException.class, () -> ClonedVoiceProfiles.validateSample(truncated))
            .getMessage().contains("cắt cụt"));
        ClonedVoiceProfiles.validateSample(TestSupport.writeWav(dir.resolve("minimum.wav"), 6));
        ClonedVoiceProfiles.validateSample(TestSupport.writeWav(dir.resolve("maximum.wav"), 60));
    }

    @Test
    void sampleHelpAndBackendLimitAreVietnameseAndDoNotTreatAudioAsAPack() throws Exception {
        Path mp3 = Files.writeString(dir.resolve("not-a-pack.mp3"), "sample");
        String message = assertThrows(NarrationException.class, () -> ClonedVoiceProfiles.validateSample(mp3)).getMessage();
        assertTrue(message.contains("WAV PCM"));
        assertTrue(message.contains("không phải gói giọng .onnx"));
        assertTrue(ClonedVoiceProfiles.CHECKING.contains("Đang kiểm tra"));
        assertTrue(ClonedVoiceProfiles.FAILED.contains("thất bại"));
        assertTrue(XttsTtsEngine.UNAVAILABLE.contains("chỉ tạo giọng đọc tiếng Anh"));
    }

    @Test
    void failedCreationDoesNotPublishOrKeepTheSample() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        XttsTtsEngine engine = engine((command, stdin, timeout) -> {
            calls.incrementAndGet();
            return new ProcessRunner.Result(1, "backend error");
        });
        Path folder = dir.resolve("voices");
        Path sample = TestSupport.writeWav(dir.resolve("sample.wav"), 6);
        ClonedVoiceProfiles profiles = new ClonedVoiceProfiles(engine);
        assertThrows(NarrationException.class, () -> profiles.create(folder, sample, "Voice", ignored -> { }));
        assertEquals(1, calls.get());
        assertTrue(profiles.discover(folder).voices().isEmpty());
        try (var files = Files.list(folder)) {
            assertEquals(0, files.count());
        }
        assertThrows(NarrationException.class, () -> profiles.create(folder, sample, " ", ignored -> { }));
        assertEquals(1, calls.get(), "invalid names never start the backend");
    }

    @Test
    void missingBackendAndMissingModelsAreUnavailableWithoutRunningAnything() throws Exception {
        ProcessRunner never = (command, stdin, timeout) -> { throw new AssertionError("must not run"); };
        XttsTtsEngine missing = new XttsTtsEngine(null, null, never);
        assertTrue(missing.unavailableReason().orElseThrow().contains("Voice cloning unavailable"));
        Path sample = TestSupport.writeWav(dir.resolve("sample.wav"), 6);
        assertThrows(NarrationException.class,
            () -> new ClonedVoiceProfiles(missing).create(dir.resolve("voices"), sample, "Voice", ignored -> { }));
        XttsTtsEngine incomplete = new XttsTtsEngine(Files.writeString(dir.resolve("tts"), "fake"), dir, never);
        assertTrue(incomplete.unavailableReason().isPresent());
        assertFalse(Files.exists(dir.resolve("voices")));
    }

    @Test
    void skipsBrokenMetadataMissingSamplesAndLinksOutsideTheFolder() throws Exception {
        XttsTtsEngine engine = engine((command, stdin, timeout) -> {
            TestSupport.writeWav(Path.of(command.get(command.indexOf("--out_path") + 1)), 1);
            return new ProcessRunner.Result(0, "");
        });
        Path folder = dir.resolve("voices");
        Path sample = TestSupport.writeWav(dir.resolve("sample.wav"), 6);
        ClonedVoiceProfiles profiles = new ClonedVoiceProfiles(engine);
        VoicePack voice = profiles.create(folder, sample, "Voice", ignored -> { });
        Files.delete(voice.model());
        assertEquals(1, profiles.discover(folder).problems().size());
        assertTrue(profiles.discover(folder).voices().isEmpty());
        Files.createSymbolicLink(voice.model(), sample);
        assertTrue(profiles.discover(folder).problems().get(0).contains("outside"));
        Files.writeString(voice.config(), "{invalid");
        assertTrue(profiles.discover(folder).voices().isEmpty());
        assertEquals(1, profiles.discover(folder).problems().size());
    }

    @Test
    void propagatesCancellationAndRejectsStaleOrInvalidOutput() throws Exception {
        XttsTtsEngine cancelled = engine((command, stdin, timeout) -> { throw new InterruptedException(); });
        Path sample = TestSupport.writeWav(dir.resolve("sample.wav"), 6);
        VoicePack voice = new VoicePack("clone-test", "Voice", "en", XttsTtsEngine.ID, sample, null, 24000, "");
        Path output = TestSupport.writeWav(dir.resolve("output.wav"), 1);
        try {
            assertThrows(NarrationException.class, () -> cancelled.synthesize(voice, "Hello", output));
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(output));
        } finally {
            Thread.interrupted();
        }
        XttsTtsEngine empty = engine((command, stdin, timeout) -> new ProcessRunner.Result(0, ""));
        assertThrows(NarrationException.class, () -> empty.synthesize(voice, "Hello", output));
        XttsTtsEngine corrupt = engine((command, stdin, timeout) -> {
            Files.write(output, new byte[100]);
            return new ProcessRunner.Result(0, "");
        });
        assertThrows(NarrationException.class, () -> corrupt.synthesize(voice, "Hello", output));
    }

    private XttsTtsEngine engine(ProcessRunner runner) throws Exception {
        Path executable = Files.writeString(dir.resolve("tts"), "fake");
        Path model = Files.createDirectories(dir.resolve("xtts-v2"));
        for (String file : List.of("model.pth", "config.json", "vocab.json")) {
            Files.writeString(model.resolve(file), "fake");
        }
        return new XttsTtsEngine(executable, model, runner);
    }
}
