package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoiceFolders;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.VoicePackRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoicePackRegistryTest {
    private static final String PIPER_CONFIG = "{\"audio\":{\"sample_rate\":22050},\"espeak\":{\"voice\":\"vi\"},"
        + "\"language\":{\"code\":\"vi_VN\"},\"dataset\":\"vais1000\",\"num_speakers\":1}";

    private final VoicePackRegistry registry = new VoicePackRegistry(Set.of("piper"));

    @Test
    void discoversPiperVoicesDroppedIntoTheFolder(@TempDir Path voices) throws IOException {
        pack(voices, "vi_VN-vais1000-medium", PIPER_CONFIG);
        Path sub = Files.createDirectories(voices.resolve("my-pack"));
        pack(sub, "en_US-amy-low", "{\"audio\":{\"sample_rate\":16000},\"language\":{\"code\":\"en_US\"}}");
        Files.writeString(sub.resolve("en_US-amy-low.voice.json"),
            "{\"name\":\"Amy (narrator)\",\"description\":\"Calm\\u0007 voice\",\"engine\":\"Piper\"}");

        VoiceDiscovery found = registry.discover(voices);
        assertEquals(2, found.voices().size(), found.problems().toString());
        assertTrue(found.problems().isEmpty(), found.problems().toString());
        VoicePack amy = found.voices().get(0);
        assertEquals("Amy (narrator)", amy.name());
        assertEquals("en_US", amy.language());
        assertEquals("en_US-amy-low", amy.id());
        assertEquals(16000, amy.sampleRate());
        assertEquals("Calm voice", amy.description());
        assertEquals("en", amy.storyLanguage());
        VoicePack vi = found.find("vi_VN-vais1000-medium").orElseThrow();
        assertEquals("Vais1000 medium", vi.name());
        assertEquals("vi_VN", vi.language());
        assertEquals("piper", vi.engine());
        assertEquals("vi", vi.storyLanguage());
        assertTrue(vi.model().isAbsolute());
    }

    @Test
    void rejectsInvalidPacksWithUsefulMessages(@TempDir Path voices) throws IOException {
        Files.write(voices.resolve("no-config.onnx"), new byte[] {1});
        pack(voices, "broken-json", "{not json");
        pack(voices, "no-rate", "{\"language\":{\"code\":\"en_US\"}}");
        pack(voices, "array", "[1]");
        pack(voices, "other-engine", PIPER_CONFIG);
        Files.writeString(voices.resolve("other-engine.voice.json"), "{\"engine\":\"coqui\"}");
        pack(voices, "bad-lang", "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"!!\"}}");
        Files.write(voices.resolve("empty.onnx"), new byte[0]);
        Files.writeString(voices.resolve("empty.onnx.json"), PIPER_CONFIG);
        pack(voices, "bad name", PIPER_CONFIG);
        Files.writeString(voices.resolve("readme.txt"), "not a voice");

        VoiceDiscovery found = registry.discover(voices);
        assertTrue(found.voices().isEmpty(), found.voices().toString());
        String problems = String.join("\n", found.problems());
        assertEquals(8, found.problems().size(), problems);
        assertTrue(problems.contains("no-config.onnx: missing config file no-config.onnx.json"), problems);
        assertTrue(problems.contains("broken-json.onnx: broken-json.onnx.json is not valid JSON"), problems);
        assertTrue(problems.contains("no-rate.onnx: the config has no valid audio.sample_rate"), problems);
        assertTrue(problems.contains("array.onnx: array.onnx.json is not a JSON object"), problems);
        assertTrue(problems.contains("unsupported engine 'coqui' (supported: piper)"), problems);
        assertTrue(problems.contains("unknown language '!!'"), problems);
        assertTrue(problems.contains("empty.onnx: the model file is empty"), problems);
        assertTrue(problems.contains("bad name.onnx: the file name may only use"), problems);
    }

    @Test
    void rejectsDuplicatesOversizedConfigsAndLinksOutsideTheFolder(@TempDir Path root) throws IOException {
        Path voices = Files.createDirectories(root.resolve("voices"));
        pack(voices, "same", PIPER_CONFIG);
        pack(Files.createDirectories(voices.resolve("copy")), "same", PIPER_CONFIG);
        pack(voices, "huge", "{\"pad\":\"" + "x".repeat(1024 * 1024) + "\"}");
        Path outside = pack(Files.createDirectories(root.resolve("elsewhere")), "secret", PIPER_CONFIG);
        boolean linked = true;
        try {
            Files.createSymbolicLink(voices.resolve("secret.onnx"), outside);
            Files.createSymbolicLink(voices.resolve("secret.onnx.json"), root.resolve("elsewhere/secret.onnx.json"));
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            linked = false; // e.g. Windows without symlink rights
        }
        VoiceDiscovery found = registry.discover(voices);
        assertEquals(1, found.voices().size());
        String problems = String.join("\n", found.problems());
        assertTrue(problems.contains("a voice with the id 'same' already exists"), problems);
        assertTrue(problems.contains("too large for a voice config"), problems);
        if (linked) {
            assertTrue(problems.contains("links outside the voices folder"), problems);
        }
    }

    @Test
    void missingFolderIsReportedNotThrown(@TempDir Path root) {
        VoiceDiscovery found = registry.discover(root.resolve("nope"));
        assertTrue(found.voices().isEmpty());
        assertTrue(found.problems().get(0).startsWith("Voices folder not found"));
    }

    @Test
    void defaultFoldersPerPlatform() {
        assertEquals(Path.of("C:/Users/A/AppData/Roaming", "MinecraftArchitect"),
            VoiceFolders.appFolder("Windows 11", "C:/Users/A/AppData/Roaming", "C:/Users/A"));
        assertEquals(Path.of("/home/a", ".minecraft-architect"), VoiceFolders.appFolder("Linux", null, "/home/a"));
        assertEquals(Path.of("/Users/a", "Library", "Application Support", "MinecraftArchitect"),
            VoiceFolders.appFolder("Mac OS X", null, "/Users/a"));
    }

    private static Path pack(Path folder, String id, String config) throws IOException {
        Path model = Files.write(folder.resolve(id + ".onnx"), new byte[] {1, 2, 3});
        Files.writeString(folder.resolve(id + ".onnx.json"), config);
        return model;
    }
}
