package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.VoiceDropInput;
import com.annaschneider.minecraft1.video.voice.VoiceInstallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceDropInputTest {
    @Test
    void distinguishesOneWavFromModelConfigPairInEitherOrder(@TempDir Path root) throws Exception {
        Path wav = Files.write(root.resolve("sample.WAV"), new byte[] {1});
        VoiceDropInput audio = VoiceDropInput.classify(List.of(wav));
        assertEquals(VoiceDropInput.Kind.WAV_SAMPLE, audio.kind());
        assertEquals(wav, audio.sample());
        assertNull(audio.model());
        Path model = Files.write(root.resolve("voice.onnx"), new byte[] {1});
        Path config = Files.writeString(root.resolve("voice.onnx.json"), "{}");
        for (List<Path> paths : List.of(List.of(model), List.of(config), List.of(model, config), List.of(config, model))) {
            VoiceDropInput pack = VoiceDropInput.classify(paths);
            assertEquals(VoiceDropInput.Kind.PIPER_PACK, pack.kind());
            assertEquals(model, pack.model());
            assertEquals(config, pack.config());
            assertNull(pack.sample());
        }
    }

    @Test
    void rejectsMixedUnsupportedDuplicateAndMismatchedSelections(@TempDir Path root) throws Exception {
        Path wav = Files.write(root.resolve("sample.wav"), new byte[] {1});
        Path model = Files.write(root.resolve("voice.onnx"), new byte[] {1});
        Path config = Files.writeString(root.resolve("voice.onnx.json"), "{}");
        Path other = Files.writeString(root.resolve("other.onnx.json"), "{}");
        Path text = Files.writeString(root.resolve("notes.txt"), "text");
        for (List<Path> selection : List.of(List.<Path>of(), List.of(wav, model), List.of(wav, config),
            List.of(model, other), List.of(model, model), List.of(config, config), List.of(wav, wav),
            List.of(text), List.of(model, config, wav), List.of(root))) {
            assertThrows(VoiceInstallException.class, () -> VoiceDropInput.classify(selection));
        }
        assertThrows(VoiceInstallException.class, () -> VoiceDropInput.classify(null));
        assertThrows(VoiceInstallException.class, () -> VoiceDropInput.classify(Arrays.asList(model, null)));
    }

    @Test
    void missingExactSiblingDoesNotUseLegacyConfig(@TempDir Path root) throws Exception {
        Path model = Files.write(root.resolve("voice.onnx"), new byte[] {1});
        Files.writeString(root.resolve("voice.json"), "{}");
        assertThrows(VoiceInstallException.class, () -> VoiceDropInput.classify(List.of(model)));
        Files.delete(model);
        Path config = Files.writeString(root.resolve("voice.onnx.json"), "{}");
        assertThrows(VoiceInstallException.class, () -> VoiceDropInput.classify(List.of(config)));
    }
}
