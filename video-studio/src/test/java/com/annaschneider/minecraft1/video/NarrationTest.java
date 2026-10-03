package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.NarrationClip;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.PiperTtsEngine;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.WavInfo;
import com.annaschneider.minecraft1.video.voice.VoicePackRegistry;
import com.annaschneider.minecraft1.video.voice.XttsTtsEngine;
import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.render.RenderOptions;
import com.annaschneider.minecraft1.video.story.StoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NarrationTest {
    @Test
    void narratesEverySceneWithText(@TempDir Path dir) throws Exception {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        Storyboard story = new Storyboard("t", "p", "en", "template", List.of(
            new Scene(0, SceneKind.INTRO, "a", "one two three", 0, 0, 5),
            new Scene(1, SceneKind.TIMELAPSE, "b", "", 0, 1, 5),
            new Scene(2, SceneKind.FINALE, "c", "four five", 1, 1, 5)));
        List<String> progress = new ArrayList<>();
        List<NarrationClip> clips = new Narrator(List.of(tts)).narrate(story, TestSupport.voice(dir), dir, progress::add);
        assertEquals(List.of("one two three", "four five"), tts.spoken);
        assertEquals(2, clips.size());
        assertEquals(0, clips.get(0).sceneIndex());
        assertEquals(1.2, clips.get(0).seconds(), 0.01);
        assertEquals(dir.resolve("narration-scene-03.wav"), clips.get(1).wav());
        assertEquals(2, progress.size());
    }

    @Test
    void joinsSceneNarrationIntoOneTrackAlignedWithTheStory(@TempDir Path dir) throws Exception {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        Storyboard story = new Storyboard("t", "p", "en", "template", List.of(
            new Scene(0, SceneKind.INTRO, "a", "one two three", 0, 0, 5),
            new Scene(1, SceneKind.TIMELAPSE, "b", "", 0, 1, 4),
            new Scene(2, SceneKind.FINALE, "c", "four five", 1, 1, 3)));
        List<NarrationClip> clips = new Narrator(List.of(tts)).narrate(story, TestSupport.voice(dir), dir, message -> { });
        java.util.Map<Integer, NarrationClip> byScene = new java.util.HashMap<>();
        clips.forEach(clip -> byScene.put(clip.sceneIndex(), clip));
        Path track = com.annaschneider.minecraft1.video.voice.NarrationTrackWriter.write(story, byScene, dir.resolve("out/track.wav"));
        assertEquals(12, WavInfo.seconds(track), 0.01);
        assertThrows(NarrationException.class, () -> com.annaschneider.minecraft1.video.voice.NarrationTrackWriter.write(story,
            java.util.Map.of(), dir.resolve("empty.wav")));
    }

    @Test
    void previewUsesASampleInTheVoiceLanguage(@TempDir Path dir) throws Exception {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        VoicePack vi = new VoicePack("vi_VN-x-low", "X", "vi_VN", "piper", dir.resolve("m.onnx"), dir.resolve("m.onnx.json"), 16000, "");
        NarrationClip clip = new Narrator(List.of(tts)).preview(vi, null, dir.resolve("preview.wav"));
        assertEquals(Narrator.previewText("vi"), tts.spoken.get(0));
        assertTrue(clip.seconds() > 0);
        assertEquals(clip.seconds(), WavInfo.seconds(dir.resolve("preview.wav")), 1e-9);
    }

    @Test
    void missingEngineOrBackendGivesAClearError(@TempDir Path dir) {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        tts.unavailable = PiperTtsEngine.MISSING_MESSAGE;
        Narrator narrator = new Narrator(List.of(tts));
        NarrationException missing = assertThrows(NarrationException.class,
            () -> narrator.preview(TestSupport.voice(dir), "hi", dir.resolve("x.wav")));
        assertTrue(missing.getMessage().contains("Piper (local text-to-speech) was not found"));

        VoicePack coqui = new VoicePack("c", "C", "en_US", "coqui", dir.resolve("c.onnx"), dir.resolve("c.json"), 22050, "");
        assertTrue(new Narrator(List.of()).unavailableReason(coqui).orElseThrow().contains("'coqui' engine"));
        assertTrue(new PiperTtsEngine(null, null).unavailableReason().isPresent());
        assertThrows(NarrationException.class, () -> WavInfo.seconds(Files.writeString(dir.resolve("bad.wav"), "nope")));
    }

    @Test
    void piperGetsTextOnStdinAndVoiceFilesAsArguments(@TempDir Path dir) throws Exception {
        Path exe = Files.writeString(dir.resolve("piper"), "");
        List<List<String>> commands = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        PiperTtsEngine piper = new PiperTtsEngine(exe, (command, stdin, timeout) -> {
            commands.add(command);
            inputs.add(stdin);
            TestSupport.writeWav(Path.of(command.get(command.size() - 1)), 1);
            return new com.annaschneider.minecraft1.video.ProcessRunner.Result(0, "");
        });
        VoicePack voice = TestSupport.voice(dir);
        Files.write(voice.model(), new byte[] {1});
        Files.writeString(voice.config(), "{\"num_speakers\":1,\"audio\":{\"sample_rate\":22050}}");
        Path out = dir.resolve("out.wav");
        piper.synthesize(voice, "Hello\nworld; rm -rf /", out);
        assertEquals(List.of(exe.toString(), "--model", voice.model().toString(), "--config", voice.config().toString(),
            "--output_file", out.toString()), commands.get(0));
        assertEquals("Hello world; rm -rf /\n", inputs.get(0));

        PiperTtsEngine failing = new PiperTtsEngine(exe, (command, stdin, timeout) ->
            new com.annaschneider.minecraft1.video.ProcessRunner.Result(1, "Load model failed\nInvalid model file"));
        NarrationException error = assertThrows(NarrationException.class, () -> failing.synthesize(voice, "hi", dir.resolve("f.wav")));
        assertTrue(error.getMessage().contains("Invalid model file"), error.getMessage());
        PiperTtsEngine hanging = new PiperTtsEngine(exe, (command, stdin, timeout) -> {
            throw new IOException("piper did not finish within " + Duration.ofMinutes(3).toSeconds() + " s");
        });
        assertThrows(NarrationException.class, () -> hanging.synthesize(voice, "hi", dir.resolve("h.wav")));
        assertThrows(NarrationException.class, () -> piper.synthesize(voice, "  \n ", dir.resolve("e.wav")));
        assertTrue(assertThrows(NarrationException.class,
            () -> piper.synthesize(voice, null, dir.resolve("e.wav"))).getMessage().contains("text is empty"));
    }

    @Test
    void previewAndNarrationPassTheSelectedNumericSpeaker(@TempDir Path dir) throws Exception {
        VoicePack voice = multiSpeaker(dir);
        Path exe = Files.writeString(dir.resolve("piper"), "");
        List<List<String>> commands = new ArrayList<>();
        PiperTtsEngine piper = new PiperTtsEngine(exe, (command, stdin, timeout) -> {
            commands.add(command);
            TestSupport.writeWav(Path.of(command.get(command.indexOf("--output_file") + 1)), 1);
            return new ProcessRunner.Result(0, "");
        });
        Narrator narrator = new Narrator(List.of(piper));
        narrator.preview(voice, null, dir.resolve("preview.wav"));
        narrator.narrate(story("en"), voice, dir, ignored -> { });
        assertEquals(2, commands.size());
        VoicePack retained = new VoicePackRegistry(Set.of("piper")).discover(dir).find(voice.id()).orElseThrow();
        assertEquals(voice, retained);
        FfmpegTool ffmpeg = new FfmpegTool(Files.writeString(dir.resolve("ffmpeg"), "fake"), new TestSupport.FakeFfmpeg());
        VideoPipeline pipeline = new VideoPipeline(StoryService.templatesOnly(), narrator, () -> Optional.of(ffmpeg));
        ExportResult export = pipeline.export(new ExportRequest(story("en"), null,
            List.of(Files.writeString(dir.resolve("footage.mp4"), "fake")), retained, dir.resolve("out"),
            "selected-speaker", RenderOptions.hd720(), false), ignored -> { });
        assertTrue(export.narrated(), export.warnings().toString());
        assertTrue(Files.isRegularFile(export.video()));
        assertEquals(3, commands.size());
        for (List<String> command : commands) {
            assertEquals("2", command.get(command.indexOf("--speaker") + 1));
            assertEquals(voice.model().toString(), command.get(command.indexOf("--model") + 1));
            assertEquals(voice.config().toString(), command.get(command.indexOf("--config") + 1));
        }
    }

    @Test
    void rejectsStaleSpeakerSelectionMissingFilesAndWrongBackendBeforeRunning(@TempDir Path dir) throws Exception {
        VoicePack voice = multiSpeaker(dir);
        PiperTtsEngine piper = new PiperTtsEngine(Files.writeString(dir.resolve("piper"), ""),
            (command, stdin, timeout) -> { throw new AssertionError("invalid voice must not run"); });
        VoicePack missingSpeaker = new VoicePack(voice.id(), voice.name(), voice.language(), voice.engine(),
            voice.model(), voice.config(), voice.sampleRate(), voice.description());
        assertTrue(assertThrows(NarrationException.class,
            () -> piper.synthesize(missingSpeaker, "hello", dir.resolve("out.wav"))).getMessage().contains("speaker ID is required"));
        Files.writeString(voice.config(), "{\"num_speakers\":2}");
        assertTrue(assertThrows(NarrationException.class,
            () -> piper.synthesize(voice, "hello", dir.resolve("out.wav"))).getMessage().contains("speaker"));
        Files.writeString(voice.config(), "{\"num_speakers\":3,\"speaker_id_map\":{\"changed\":2}}");
        assertThrows(NarrationException.class, () -> piper.synthesize(voice, "hello", dir.resolve("out.wav")));
        Files.writeString(voice.config(), "{\"num_speakers\":3,\"speaker_id_map\":{\"a\":0,\"b\":0}}");
        assertThrows(NarrationException.class, () -> piper.synthesize(voice, "hello", dir.resolve("out.wav")));
        Files.delete(voice.config());
        assertTrue(assertThrows(NarrationException.class,
            () -> piper.synthesize(voice, "hello", dir.resolve("out.wav"))).getMessage().contains("missing model config"));
        Files.delete(voice.model());
        assertTrue(assertThrows(NarrationException.class,
            () -> piper.synthesize(voice, "hello", dir.resolve("out.wav"))).getMessage().contains("Missing Piper model"));
        VoicePack clone = new VoicePack("clone", "Clone", "en", XttsTtsEngine.ID, voice.model(), voice.config(), 24000, "");
        assertTrue(assertThrows(NarrationException.class,
            () -> piper.synthesize(clone, "hello", dir.resolve("out.wav"))).getMessage().contains("backend"));
    }

    @Test
    void rejectsUnsupportedLanguagesAndMismatchesBeforeSpeaking(@TempDir Path dir) throws Exception {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        Narrator narrator = new Narrator(List.of(tts));
        VoicePack english = TestSupport.voice(dir);
        assertThrows(NarrationException.class, () -> narrator.narrate(story("vi"), english, dir, ignored -> { }));
        assertThrows(NarrationException.class, () -> narrator.narrate(story("fr"), english, dir, ignored -> { }));
        VoicePack vietnamese = new VoicePack("vi", "Vi", "vi_VN", "piper", english.model(), english.config(), 22050, "");
        assertThrows(NarrationException.class, () -> narrator.narrate(story("en"), vietnamese, dir, ignored -> { }));
        VoicePack french = new VoicePack("fr", "French", "fr_FR", "piper", english.model(), english.config(), 22050, "");
        assertEquals("fr", french.storyLanguage());
        assertTrue(narrator.unavailableReason(french).orElseThrow().contains("Unsupported narration language"));
        assertThrows(NarrationException.class, () -> narrator.preview(french, "bonjour", dir.resolve("preview.wav")));
        assertThrows(IllegalArgumentException.class, () -> Narrator.previewText("fr"));
        assertTrue(tts.spoken.isEmpty());
        narrator.narrate(story("en-US"), english, dir, ignored -> { });
        assertEquals(1, tts.spoken.size());
        TestSupport.FakeTts xtts = new TestSupport.FakeTts() {
            @Override public String id() { return XttsTtsEngine.ID; }
        };
        VoicePack wrongClone = new VoicePack("clone", "Clone", "vi", XttsTtsEngine.ID, english.model(), english.config(), 24000, "");
        assertTrue(new Narrator(List.of(xtts)).unavailableReason(wrongClone).orElseThrow().contains("English"));
    }

    @Test
    void preservesTheRegisteredEngineSpiForPreviewAndNarration(@TempDir Path dir) throws Exception {
        TestSupport.FakeTts custom = new TestSupport.FakeTts() {
            @Override public String id() { return "custom-tts"; }
        };
        VoicePack voice = new VoicePack("custom", "Custom", "en_US", custom.id(),
            dir.resolve("custom.model"), dir.resolve("custom.config"), 22050, "");
        Narrator narrator = new Narrator(List.of(custom));
        assertTrue(narrator.unavailableReason(voice).isEmpty());
        narrator.preview(voice, "custom preview", dir.resolve("custom-preview.wav"));
        narrator.narrate(story("en"), voice, dir, ignored -> { });
        assertEquals(List.of("custom preview", "hello"), custom.spoken);
        assertTrue(new Narrator(List.of()).unavailableReason(voice).orElseThrow().contains("'custom-tts' engine"));
    }

    private static Storyboard story(String language) {
        return new Storyboard("t", "p", language, "template",
            List.of(new Scene(0, SceneKind.INTRO, "a", "hello", 0, 0, 5)));
    }

    private static VoicePack multiSpeaker(Path dir) throws Exception {
        Path model = Files.write(dir.resolve("en_US-multi-medium.onnx"), new byte[] {1});
        Files.writeString(dir.resolve("en_US-multi-medium.onnx.json"),
            "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"en_US\"},"
                + "\"num_speakers\":3,\"speaker_id_map\":{\"first\":0,\"second\":1,\"third\":2}}");
        return new VoicePackRegistry(Set.of("piper")).loadSpeakers(dir, model).get(2);
    }
}
