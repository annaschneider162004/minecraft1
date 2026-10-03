package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.voice.VoiceDropInput;
import com.annaschneider.minecraft1.video.voice.VoiceInstallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DesktopVoiceSupportTest {
    @Test
    void routesWavAndPackDropsWithoutTreatingAudioAsTts(@TempDir Path dir) throws Exception {
        Path sample = Files.write(dir.resolve("sample.wav"), new byte[] {1});
        Path model = Files.write(dir.resolve("voice.onnx"), new byte[] {1});
        Path config = Files.writeString(dir.resolve("voice.onnx.json"), "{}");
        assertEquals(VoiceDropInput.Kind.WAV_SAMPLE, DesktopVoiceSupport.classifyFiles(List.of(sample), false).kind());
        assertEquals(sample, DesktopVoiceSupport.classifyFiles(List.of(sample), false).sample());
        var pack = DesktopVoiceSupport.classifyFiles(List.of(config, model), false);
        assertEquals(VoiceDropInput.Kind.PIPER_PACK, pack.kind());
        assertEquals(model, pack.model());
        assertEquals(config, pack.config());
        assertEquals(config, DesktopVoiceSupport.classifyFiles(List.of(model), false).config());
        assertThrows(VoiceInstallException.class, () -> DesktopVoiceSupport.classifyFiles(List.of(sample), true));
        assertThrows(VoiceInstallException.class, () -> DesktopVoiceSupport.classifyFiles(List.of(sample, model), false));
        Path audio = Files.write(dir.resolve("audio.mp3"), new byte[] {1});
        assertThrows(VoiceInstallException.class, () -> DesktopVoiceSupport.classifyFiles(List.of(audio), false));
    }

    @Test
    void importedVoiceOnlyReplacesSelectionAfterExplicitChoice() {
        assertEquals("saved", DesktopVoiceSupport.selectionAfterImport("saved", "imported", false));
        assertEquals("", DesktopVoiceSupport.selectionAfterImport("", "imported", false));
        assertEquals("imported", DesktopVoiceSupport.selectionAfterImport("saved", "imported", true));
    }

    @Test
    void localizedGuidanceClearlySeparatesPackAndEnglishOnlyCloning() {
        assertTrue(DesktopVoiceSupport.DROP_HINT.contains("WAV PCM"));
        assertTrue(DesktopVoiceSupport.DROP_HINT.contains(".onnx + .onnx.json"));
        assertTrue(DesktopVoiceSupport.SAMPLE_GUIDANCE.contains("6–60 giây"));
        assertTrue(DesktopVoiceSupport.SAMPLE_GUIDANCE.contains("không hỗ trợ nhân bản tiếng Việt"));
        assertTrue(DesktopVoiceSupport.ENGLISH_SAMPLE.contains("Minecraft adventure"));
        assertTrue(DesktopVoiceSupport.VIETNAMESE_REFERENCE.contains("vương quốc"));
    }
}
