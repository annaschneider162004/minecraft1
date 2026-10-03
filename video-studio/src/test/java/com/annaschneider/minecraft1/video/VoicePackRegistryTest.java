package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoiceFolders;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.VoicePackRegistry;
import com.annaschneider.minecraft1.video.voice.InvalidVoicePackException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void preservesCaseInsensitiveModelExtensionDiscovery(@TempDir Path voices) throws Exception {
        Path model = Files.write(voices.resolve("en_US-upper-low.ONNX"), new byte[] {1});
        Files.writeString(voices.resolve("en_US-upper-low.onnx.json"),
            "{\"audio\":{\"sample_rate\":16000},\"language\":{\"code\":\"en_US\"}}");
        VoiceDiscovery discovery = registry.discover(voices);
        assertTrue(discovery.problems().isEmpty(), discovery.problems().toString());
        assertEquals(List.of(registry.load(voices, model)), discovery.voices());
        assertEquals("en_US-upper-low", discovery.voices().get(0).id());
    }

    @Test
    void expandsSharedSpeakersWithStableIdsRegardlessOfMapOrder(@TempDir Path voices) throws Exception {
        Path model = pack(voices, "en_US-many-medium", speakerConfig(
            "\"num_speakers\":3,\"speaker_id_map\":{\"zebra\":2,\"alpha\":0,\"beta\":1}"));
        List<VoicePack> speakers = registry.loadSpeakers(voices, model);
        assertEquals(List.of("en_US-many-medium", "en_US-many-medium-speaker-1", "en_US-many-medium-speaker-2"),
            speakers.stream().map(VoicePack::id).toList());
        assertEquals(List.of(0, 1, 2), speakers.stream().map(VoicePack::speakerId).toList());
        assertEquals(List.of("alpha", "beta", "zebra"), speakers.stream().map(VoicePack::speakerIdentity).toList());
        for (VoicePack speaker : speakers) {
            assertEquals(model.toAbsolutePath(), speaker.model());
            assertEquals(speakers.get(0).config(), speaker.config());
            assertTrue(speaker.name().contains(speaker.speakerIdentity()));
        }
        assertEquals(speakers.get(0).speakerId(), registry.load(voices, model).speakerId());
        Map<String, VoicePack> first = registry.discover(voices).voices().stream()
            .collect(Collectors.toMap(VoicePack::id, v -> v));
        Files.writeString(speakers.get(0).config(), speakerConfig(
            "\"speaker_id_map\":{\"beta\":1,\"alpha\":0,\"zebra\":2},\"num_speakers\":3"));
        assertEquals(first, registry.discover(voices).voices().stream().collect(Collectors.toMap(VoicePack::id, v -> v)));
    }

    @Test
    void preservesLegacySingleSpeakerAndHonorsValidatedExplicitMetadata(@TempDir Path voices) throws Exception {
        Path legacy = pack(voices, "en_US-legacy-low", speakerConfig("\"dataset\":\"legacy\""));
        assertNull(registry.load(voices, legacy).speakerId());
        assertEquals(1, registry.loadSpeakers(voices, legacy).size());
        Path model = pack(voices, "en_US-many-low", speakerConfig(
            "\"num_speakers\":3,\"speaker_id_map\":{\"a\":0,\"b\":1,\"c\":2}"));
        Path metadata = voices.resolve("en_US-many-low.voice.json");
        Files.writeString(metadata, "{\"speaker_id\":2,\"speaker_identity\":\"c\"}");
        assertEquals(2, registry.load(voices, model).speakerId());
        assertEquals("c", registry.load(voices, model).speakerIdentity());
        assertEquals("en_US-many-low-speaker-2", registry.load(voices, model).id());
        assertEquals(registry.loadSpeakers(voices, model).get(2), registry.load(voices, model));
        assertEquals(0, registry.loadSpeakers(voices, model).get(0).speakerId(), "discovery preserves speaker zero's model ID");
        Files.writeString(metadata, "{\"speaker_identity\":\"b\"}");
        assertEquals(1, registry.load(voices, model).speakerId());
        for (String invalid : List.of("{\"speaker_id\":3}", "{\"speaker_id\":-1}", "{\"speaker_id\":\"1\"}",
            "{\"speaker_id\":1.5}", "{\"speaker_id\":null}", "{\"speaker_id\":false}",
            "{\"speaker_identity\":\"unknown\"}", "{\"speaker_identity\":3}",
            "{\"speaker_identity\":null}", "{\"speaker_identity\":\"\"}", "{\"speaker_id\":1,\"speaker_identity\":\"c\"}")) {
            Files.writeString(metadata, invalid);
            assertThrows(InvalidVoicePackException.class, () -> registry.load(voices, model), invalid);
            assertThrows(InvalidVoicePackException.class, () -> registry.loadSpeakers(voices, model), invalid);
        }
    }

    @Test
    void rejectsMalformedSpeakerCountsAndMapsWithoutFallback(@TempDir Path voices) throws Exception {
        Path model = pack(voices, "en_US-many-low", PIPER_CONFIG);
        for (String invalid : List.of("\"num_speakers\":0", "\"num_speakers\":-2", "\"num_speakers\":1.5",
            "\"num_speakers\":\"2\"", "\"num_speakers\":null", "\"num_speakers\":true", "\"num_speakers\":10001",
            "\"num_speakers\":2147483648", "\"num_speakers\":2,\"speaker_id_map\":[]",
            "\"num_speakers\":2,\"speaker_id_map\":null", "\"num_speakers\":2,\"speaker_id_map\":{\"a\":0,\"b\":0}",
            "\"num_speakers\":2,\"speaker_id_map\":{\"a\":-1}", "\"num_speakers\":2,\"speaker_id_map\":{\"a\":2}",
            "\"num_speakers\":2,\"speaker_id_map\":{\"a\":\"1\"}", "\"num_speakers\":2,\"speaker_id_map\":{\"a\":1.2}",
            "\"num_speakers\":2,\"speaker_id_map\":{\"a\":true}", "\"num_speakers\":2,\"speaker_id_map\":{\"\":0}",
            "\"speaker_id_map\":{\"a\":1}")) {
            Files.writeString(voices.resolve("en_US-many-low.onnx.json"), speakerConfig(invalid));
            InvalidVoicePackException error = assertThrows(InvalidVoicePackException.class, () -> registry.load(voices, model), invalid);
            assertTrue(error.getMessage().contains("speaker"), error.getMessage());
            VoiceDiscovery discovery = registry.discover(voices);
            assertTrue(discovery.voices().isEmpty(), invalid);
            assertEquals(1, discovery.problems().size(), invalid);
        }
    }

    @Test
    void supportsLargeModelsAndRejectsDuplicateExpandedIdsAtomically(@TempDir Path voices) throws Exception {
        pack(voices, "en_US-many-medium", speakerConfig("\"num_speakers\":1200"));
        pack(Files.createDirectories(voices.resolve("copy")), "en_US-many-medium", speakerConfig("\"num_speakers\":1200"));
        VoiceDiscovery discovery = registry.discover(voices);
        assertEquals(1200, discovery.voices().size());
        assertEquals(1200, discovery.voices().stream().map(VoicePack::id).distinct().count());
        assertEquals(1, discovery.problems().size());
        assertTrue(discovery.problems().get(0).contains("already exists"));
        assertEquals(1199, discovery.find("en_US-many-medium-speaker-1199").orElseThrow().speakerId());
    }

    private static String speakerConfig(String fields) {
        return "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"en_US\"}," + fields + "}";
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
