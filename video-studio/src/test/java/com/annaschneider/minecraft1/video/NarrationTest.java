package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.NarrationClip;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.PiperTtsEngine;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.WavInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
    }
}
