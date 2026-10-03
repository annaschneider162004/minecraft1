package com.annaschneider.minecraft1.video.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalVoicePackImporterTest {
    private static final String CONFIG = """
        {"audio":{"sample_rate":22050},"language":{"code":"vi_VN"},"num_speakers":1}
        """;
    private final LocalVoicePackImporter importer = new LocalVoicePackImporter();
    private final VoicePackRegistry registry = new VoicePackRegistry(Set.of("piper"));

    @Test
    void importsExactPairWithOptionalMetadataWithoutChangingSources(@TempDir Path root) throws Exception {
        Path model = source(root, "vi_VN-local-medium", CONFIG);
        Path voices = root.resolve("voices");
        VoicePack pack = importer.importPack(model, model.resolveSibling(model.getFileName() + ".json"),
            voices, "Giọng đọc\u0007 riêng", "Mô tả");
        assertEquals("vi_VN-local-medium", pack.id());
        assertEquals("Giọng đọc riêng", pack.name());
        assertEquals("Mô tả", pack.description());
        assertEquals(22050, pack.sampleRate());
        assertEquals("piper", pack.engine());
        assertEquals(voices.resolve(pack.id()).resolve(model.getFileName()).toAbsolutePath(), pack.model());
        assertArrayEquals(Files.readAllBytes(model), Files.readAllBytes(pack.model()));
        assertEquals(CONFIG, Files.readString(pack.config()));
        assertTrue(Files.exists(model));
        assertEquals(List.of(pack), registry.discover(voices).voices());
        try (Stream<Path> children = Files.list(voices)) {
            assertEquals(List.of(voices.resolve(pack.id())), children.toList());
        }
    }

    @Test
    void findsExactSiblingConfigAndUsesRegistryDefaults(@TempDir Path root) throws Exception {
        Path model = source(root, "local", CONFIG);
        VoicePack pack = importer.importPack(model, null, root.resolve("voices"), null, null);
        assertEquals("Local", pack.name());
        assertEquals("", pack.description());
        assertEquals("vi_VN", pack.language());
    }

    @Test
    void boundsMetadataBeforeWritingAndPreservesOriginalId(@TempDir Path root) throws Exception {
        Path model = source(root, "Vi_VN-My+Voice.v2", CONFIG);
        Path voices = root.resolve("voices");
        VoicePack pack = importer.importPack(model, null, voices,
            "\u0007" + "n".repeat(1024 * 1024), "\u200b" + "d".repeat(1024 * 1024));
        assertEquals("Vi_VN-My+Voice.v2", pack.id());
        assertEquals("n".repeat(60), pack.name());
        assertEquals("d".repeat(200), pack.description());
        Path metadata = pack.model().resolveSibling(pack.id() + VoicePackRegistry.METADATA_SUFFIX);
        assertTrue(Files.size(metadata) < 1024);
        assertEquals(List.of(pack), registry.discover(voices).voices());
    }

    @Test
    void rejectsMissingAndMismatchedConfigAndLegacyFallback(@TempDir Path root) throws Exception {
        Path model = source(root, "local", CONFIG);
        Path config = model.resolveSibling("local.onnx.json");
        Files.move(config, config.resolveSibling("local.json"));
        VoiceInstallException missing = assertThrows(VoiceInstallException.class,
            () -> importer.importPack(model, null, root.resolve("voices"), null, null));
        assertTrue(missing.getMessage().contains("Không đọc được"));
        Path other = Files.writeString(model.resolveSibling("other.onnx.json"), CONFIG);
        assertThrows(VoiceInstallException.class,
            () -> importer.importPack(model, other, root.resolve("voices"), null, null));
        assertFalse(Files.exists(root.resolve("voices")));
    }

    @Test
    void invalidConfigsRollBackStagedFiles(@TempDir Path root) throws Exception {
        List<String> invalid = List.of("{bad", "[]", "{}", """
            {"audio":{"sample_rate":22050},"language":{"code":"!!"}}
            """, """
            {"audio":{"sample_rate":22050.5},"language":{"code":"vi_VN"}}
            """, """
            {"audio":{"sample_rate":22050},"language":{"code":"vi_VN"},"num_speakers":0}
            """, """
            {"audio":{"sample_rate":22050},"language":{"code":"vi_VN"},"num_speakers":2,
             "speaker_id_map":{"a":0,"b":0}}
            """, """
            {"audio":{"sample_rate":22050},"language":{"code":"vi_VN"},"num_speakers":2,
             "speaker_id_map":{"a":0.5}}
            """, """
            {"audio":{"sample_rate":22050},"language":{"code":"vi_VN"},"num_speakers":1,
             "speaker_id_map":{"a":1}}
            """);
        Path voices = Files.createDirectory(root.resolve("voices"));
        for (int index = 0; index < invalid.size(); index++) {
            Path model = source(root, "bad-" + index, invalid.get(index));
            assertThrows(VoiceInstallException.class, () -> importer.importPack(model, null, voices, null, null));
            assertEmpty(voices);
            assertTrue(registry.discover(voices).voices().isEmpty());
        }
    }

    @Test
    void rejectsEmptyOversizedAndAudioRenamedAsModel(@TempDir Path root) throws Exception {
        Path voices = Files.createDirectory(root.resolve("voices"));
        Path model = source(root, "local", CONFIG);
        List<byte[]> invalid = List.of(new byte[0], "RIFF1234WAVEdata".getBytes(),
            "RIFX1234WAVEdata".getBytes(), "RF641234WAVEdata".getBytes(), "OggSdata".getBytes(),
            "fLaCdata".getBytes(), "ID3data".getBytes(), "FORM1234AIFF".getBytes(), ".snddata".getBytes(),
            new byte[] {(byte) 0xff, (byte) 0xfb, 1, 2});
        for (byte[] bytes : invalid) {
            Files.write(model, bytes);
            assertThrows(VoiceInstallException.class, () -> importer.importPack(model, null, voices, null, null));
            assertEmpty(voices);
        }
        Files.write(model, new byte[] {1, 2, 3});
        Files.writeString(model.resolveSibling("local.onnx.json"), " ".repeat(1024 * 1024 + 1));
        assertThrows(VoiceInstallException.class, () -> importer.importPack(model, null, voices, null, null));
        assertEmpty(voices);
    }

    @Test
    void neverOverwritesExistingDirectoriesPacksOrProfilesCaseInsensitively(@TempDir Path root) throws Exception {
        Path voices = Files.createDirectory(root.resolve("voices"));
        Path model = source(root, "local", CONFIG);
        Path emptyDirectory = Files.createDirectory(voices.resolve("local"));
        assertThrows(VoiceInstallException.class, () -> importer.importPack(model, null, voices, null, null));
        assertTrue(Files.isDirectory(emptyDirectory));
        Files.delete(emptyDirectory);
        for (String name : List.of("LOCAL.onnx", "local.onnx.json", "local.voice.json",
            "local.cloned.json", "local.sample.wav", "local.json")) {
            Path existing = Files.writeString(voices.resolve(name), "keep");
            VoiceInstallException collision = assertThrows(VoiceInstallException.class,
                () -> importer.importPack(model, null, voices, null, null));
            assertTrue(collision.getMessage().contains("không ghi đè"));
            assertEquals("keep", Files.readString(existing));
            Files.delete(existing);
            assertEmpty(voices);
        }
        Path sub = Files.createDirectory(voices.resolve("old"));
        Path existingModel = Files.writeString(sub.resolve("LOCAL.onnx"), "keep");
        assertThrows(VoiceInstallException.class, () -> importer.importPack(model, null, voices, null, null));
        assertEquals("keep", Files.readString(existingModel));
    }

    @Test
    void failedAndCancelledCopiesLeaveNoVisiblePack(@TempDir Path root) throws Exception {
        Path model = source(root, "local", CONFIG);
        Path notFolder = Files.writeString(root.resolve("not-folder"), "keep");
        assertThrows(VoiceInstallException.class, () -> importer.importPack(model, null, notFolder, null, null));
        assertEquals("keep", Files.readString(notFolder));
        Path voices = Files.createDirectory(root.resolve("voices"));
        Thread.currentThread().interrupt();
        try {
            assertThrows(VoiceInstallException.class, () -> importer.importPack(model, null, voices, null, null));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEmpty(voices);
        assertTrue(Files.exists(model));
    }

    @Test
    void concurrentCaseVariantImportsPublishOnlyOnePack(@TempDir Path root) throws Exception {
        Path lower = source(root, "local", CONFIG);
        Path upper = source(root, "LOCAL", CONFIG);
        Path voices = Files.createDirectory(root.resolve("voices"));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> importAfter(start, lower, voices));
            var second = pool.submit(() -> importAfter(start, upper, voices));
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
            assertEquals(1, registry.discover(voices).voices().size());
            try (Stream<Path> files = Files.list(voices)) {
                assertEquals(1, files.count());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unsupportedAtomicPublicationRollsBackTransferredCompanions(@TempDir Path root) throws Exception {
        Path model = source(root, "local", CONFIG);
        Path voices = Files.createDirectory(root.resolve("voices"));
        LocalVoicePackImporter unsupported = new LocalVoicePackImporter((staged, destination) -> {
            assertTrue(Files.exists(destination.resolveSibling("local.onnx.json")));
            assertTrue(Files.exists(destination.resolveSibling("local.voice.json")));
            assertTrue(registry.discover(voices).voices().isEmpty());
            throw new AtomicMoveNotSupportedException(staged.toString(), destination.toString(), "test");
        });
        assertThrows(VoiceInstallException.class, () -> unsupported.importPack(model, null, voices, null, null));
        assertEmpty(voices);
        assertTrue(registry.discover(voices).voices().isEmpty());
    }

    private int importAfter(CountDownLatch start, Path model, Path voices) throws InterruptedException {
        start.await();
        try {
            importer.importPack(model, null, voices, null, null);
            return 1;
        } catch (VoiceInstallException ex) {
            assertTrue(ex.getMessage().contains("không ghi đè"), ex.getMessage());
            return 0;
        }
    }

    private static Path source(Path root, String id, String config) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path model = Files.write(source.resolve(id + ".onnx"), new byte[] {1, 2, 3});
        Files.writeString(source.resolve(id + ".onnx.json"), config);
        return model;
    }

    private static void assertEmpty(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(0, files.count());
        }
    }
}
