package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.VoiceCatalog;
import com.annaschneider.minecraft1.video.voice.VoiceModelInstaller;
import com.annaschneider.minecraft1.video.voice.VoicePackRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VoiceModelInstallerTest {
    private static final String ID = "en_US-test_only-medium";
    private static final String PREFIX = "https://huggingface.co/rhasspy/piper-voices/resolve/" + "a".repeat(40) + "/";
    private static final byte[] MODEL = {1, 2, 3, 4};
    private static final byte[] CONFIG = ("{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"en_US\"},"
        + "\"num_speakers\":2,\"speaker_id_map\":{\"TEST_ONLY_A\":0,\"TEST_ONLY_B\":1}}")
        .getBytes(StandardCharsets.UTF_8);

    @Test
    void explicitInstallIsAtomicAndAllSpeakersReuseOneModel(@TempDir Path root) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        var installer = new VoiceModelInstaller(uri -> {
            requests.incrementAndGet();
            return ok(uri);
        });
        var model = model();
        var progress = new ArrayList<VoiceModelInstaller.Progress>();
        assertThrows(IOException.class, () -> installer.install(model, root, false, progress::add));
        assertEquals(0, requests.get());
        Path installed = installer.install(model, root, true, p -> {
            assertFalse(Files.exists(root.resolve(ID)), "Partial models must never be published");
            progress.add(p);
        });
        assertEquals(root.resolve(ID), installed);
        var discovered = new VoicePackRegistry(Set.of("piper")).discover(root);
        assertEquals(2, discovered.voices().size(), discovered.problems().toString());
        assertEquals(discovered.voices().get(0).model(), discovered.voices().get(1).model());
        assertEquals(model.downloadBytes(), progress.get(progress.size() - 1).downloadedBytes());
        assertEquals(installed, installer.install(model, root, true, p -> { }));
        assertEquals(2, requests.get(), "Installing another speaker must reuse shared files, without network");
        assertNoPartials(root);
    }

    @Test
    void reusesManualInstallationAndDoesNotOverwriteDamagedFiles(@TempDir Path root) throws Exception {
        Files.write(root.resolve(ID + ".onnx"), MODEL);
        Files.write(root.resolve(ID + ".onnx.json"), CONFIG);
        var installer = new VoiceModelInstaller(uri -> fail("No network for installed shared model"));
        assertEquals(root, installer.install(model(), root, true, p -> { }));
        Files.write(root.resolve(ID + ".onnx"), new byte[] {9, 9, 9, 9});
        assertThrows(IOException.class, () -> installer.install(model(), root, true, p -> { }));
        assertArrayEquals(new byte[] {9, 9, 9, 9}, Files.readAllBytes(root.resolve(ID + ".onnx")));
    }

    @Test
    void legacyManualPackDoesNotNeedRedownloadForMissingOptionalCard(@TempDir Path root) throws Exception {
        Files.write(root.resolve(ID + ".onnx"), MODEL);
        Files.write(root.resolve(ID + ".onnx.json"), CONFIG);
        var files = new ArrayList<>(model().files());
        files.add(asset("MODEL_CARD", "TEST ONLY license card".getBytes(StandardCharsets.UTF_8)));
        var installer = new VoiceModelInstaller(uri -> fail("Do not redownload a legacy shared model"));
        assertEquals(root, installer.install(withFiles(files), root, true, p -> { }));
        assertFalse(Files.exists(root.resolve("MODEL_CARD")), "Do not mutate manual installations");
    }

    @Test
    void findsValidSharedCopyEvenWhenAnotherCandidateIsDamaged(@TempDir Path root) throws Exception {
        Files.write(root.resolve(ID + ".onnx"), new byte[] {9});
        Path valid = Files.createDirectory(root.resolve("valid-manual-pack"));
        Files.write(valid.resolve(ID + ".onnx"), MODEL);
        Files.write(valid.resolve(ID + ".onnx.json"), CONFIG);
        var installer = new VoiceModelInstaller(uri -> fail("Valid shared copy must be reused"));
        assertEquals(valid, installer.install(model(), root, true, p -> { }));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(root.resolve(ID + ".onnx")));
    }

    @Test
    void documentedReleaseTargetIsAcceptedWithAuthoritativeChecksums(@TempDir Path root) throws Exception {
        var releaseFiles = model().files().stream().map(a -> new VoiceCatalog.Asset(a.path(),
            URI.create(a.url().toString().replace("a".repeat(40), "v1.0.0")), a.sizeBytes(), a.hashAlgorithm(), a.hash()))
            .toList();
        assertEquals(root.resolve(ID), new VoiceModelInstaller(VoiceModelInstallerTest::ok)
            .install(withFiles(releaseFiles), root, true, p -> { }));
    }

    @Test
    void failedPartialDownloadCleansUpAndRetrySucceeds(@TempDir Path root) throws Exception {
        var broken = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(200, null, -1,
            new InputStream() {
                int reads;
                public int read() throws IOException {
                    if (reads++ == 0) { return 1; }
                    throw new IOException("Simulated interrupted connection");
                }
            }));
        assertThrows(IOException.class, () -> broken.install(model(), root, true, p -> { }));
        assertFalse(Files.exists(root.resolve(ID)));
        assertNoPartials(root);
        assertEquals(root.resolve(ID), new VoiceModelInstaller(VoiceModelInstallerTest::ok)
            .install(model(), root, true, p -> { }));
    }

    @Test
    void cancellationAndInterruptionRemoveStagingFiles(@TempDir Path root) throws Exception {
        var installer = new VoiceModelInstaller(VoiceModelInstallerTest::ok);
        assertThrows(CancellationException.class, () -> installer.install(model(), root, true, p -> {
            throw new CancellationException("user cancelled");
        }));
        assertNoPartials(root);
        try {
            assertThrows(InterruptedException.class, () -> installer.install(model(), root, true, p -> {
                Thread.currentThread().interrupt();
            }));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertFalse(Files.exists(root.resolve(ID)));
        assertNoPartials(root);
    }

    @Test
    void concurrentRequestsCannotPublishDuplicateSharedModels(@TempDir Path root) throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        VoiceCatalog.Model model = model();
        var first = new VoiceModelInstaller(uri -> {
            entered.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IOException("Test timed out");
            }
            return ok(uri);
        });
        try {
            var future = executor.submit(() -> first.install(model, root, true, p -> { }));
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var duplicate = new VoiceModelInstaller(uri -> fail("Locked model must not start another download"));
            assertThrows(IOException.class, () -> duplicate.install(model, root, true, p -> { }));
            release.countDown();
            assertEquals(root.resolve(ID), future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertNoPartials(root);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void stalledBodyHasDeadlineAndIsClosedBeforeRetry(@TempDir Path root) throws Exception {
        StalledBody body = new StalledBody();
        var installer = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(200, null, -1, body),
            Duration.ofMillis(100));
        IOException failure = assertThrows(IOException.class, () -> installer.install(model(), root, true, p -> { }));
        assertTrue(failure.getMessage().contains("timed out"), failure.getMessage());
        assertEquals(0, body.closed.getCount(), "Timeout must close the body, not just interrupt its reader");
        assertNoPartials(root);
        assertEquals(root.resolve(ID), new VoiceModelInstaller(VoiceModelInstallerTest::ok)
            .install(model(), root, true, p -> { }));
    }

    @Test
    void interruptClosesBodyEvenWhenStreamSwallowsInterrupts(@TempDir Path root) throws Exception {
        StalledBody body = new StalledBody();
        var installer = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(200, null, -1, body));
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var owner = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        VoiceCatalog.Model model = model();
        try {
            var future = executor.submit(() -> {
                owner.set(Thread.currentThread());
                try {
                    return installer.install(model, root, true, p -> { });
                } catch (InterruptedException ex) {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    throw ex;
                }
            });
            assertTrue(body.entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            owner.get().interrupt();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> future.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertTrue(interrupted.get(), "Cancellation must preserve the installer thread's interrupt status");
            assertTrue(body.closed.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertNoPartials(root);
            assertEquals(root.resolve(ID), new VoiceModelInstaller(VoiceModelInstallerTest::ok)
                .install(model, root, true, p -> { }));
        } finally {
            body.close();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsHashSizeAndInvalidConfigBeforePublication(@TempDir Path root) throws Exception {
        for (byte[] bytes : List.of(new byte[] {9, 9, 9, 9}, new byte[] {1}, new byte[] {1, 2, 3, 4, 5})) {
            var installer = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(200, null, -1,
                new ByteArrayInputStream(bytes)));
            assertThrows(IOException.class, () -> installer.install(model(), root, true, p -> { }));
            assertFalse(Files.exists(root.resolve(ID)));
            assertNoPartials(root);
        }
        var badSize = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(200, null, 99,
            new ByteArrayInputStream(MODEL)));
        assertThrows(IOException.class, () -> badSize.install(model(), root, true, p -> { }));
        byte[] wrong = "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"vi_VN\"}}".getBytes(StandardCharsets.UTF_8);
        var badModel = withFiles(List.of(asset(ID + ".onnx", MODEL), asset(ID + ".onnx.json", wrong)));
        var invalidConfig = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(200, null, -1,
            new ByteArrayInputStream(uri.getPath().endsWith(".onnx") ? MODEL : wrong)));
        assertThrows(IOException.class, () -> invalidConfig.install(badModel, root, true, p -> { }));
        assertNoPartials(root);
    }

    @Test
    void validatesEveryRedirectAndClosesRejectedBodies(@TempDir Path root) throws Exception {
        AtomicInteger closed = new AtomicInteger();
        var untrusted = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(302,
            URI.create("https://evil.example/model.onnx"), -1, new ByteArrayInputStream(new byte[0]) {
                @Override public void close() { closed.incrementAndGet(); }
            }));
        assertThrows(IOException.class, () -> untrusted.install(model(), root, true, p -> { }));
        assertEquals(1, closed.get());
        AtomicInteger requests = new AtomicInteger();
        var trusted = new VoiceModelInstaller(uri -> {
            requests.incrementAndGet();
            if ("huggingface.co".equals(uri.getHost())) {
                return new VoiceModelInstaller.Response(307, URI.create("https://cdn-lfs.hf.co" + uri.getPath()),
                    -1, new ByteArrayInputStream(new byte[0]));
            }
            return ok(uri);
        });
        trusted.install(model(), root, true, p -> { });
        assertEquals(4, requests.get());
        assertNoPartials(root);
    }

    @Test
    void rejectsRedirectLoopsHttpTraversalExecutablesAndUnpinnedSources(@TempDir Path root) throws Exception {
        var loop = new VoiceModelInstaller(uri -> new VoiceModelInstaller.Response(302, uri, -1,
            new ByteArrayInputStream(new byte[0])));
        assertThrows(IOException.class, () -> loop.install(model(), root, true, p -> { }));
        for (String url : List.of("http://huggingface.co/file", "https://huggingface.co.evil.example/file",
            "https://user@huggingface.co/file", "https://huggingface.co:444/file", "file:///tmp/model")) {
            assertThrows(IOException.class, () -> VoiceModelInstaller.validateUrl(URI.create(url)));
        }
        var installer = new VoiceModelInstaller(uri -> fail("Invalid manifest must be rejected before network"));
        for (String path : List.of("../" + ID + ".onnx", "en/../../" + ID + ".onnx", "en/piper.exe",
            "en/" + ID + ".onnx", "en\\model.onnx", "/en/" + ID + ".onnx")) {
            var bad = new VoiceCatalog.Asset(path, URI.create(PREFIX + "en/" + ID + ".onnx"), 4, "MD5",
                asset(ID + ".onnx", MODEL).hash());
            if (path.equals("en/" + ID + ".onnx")) {
                bad = new VoiceCatalog.Asset(path, URI.create(PREFIX.replace("a".repeat(40), "main") + path),
                    4, "MD5", bad.hash());
            }
            var invalid = withFiles(List.of(bad, asset(ID + ".onnx.json", CONFIG)));
            assertThrows(IOException.class, () -> installer.install(invalid, root, true, p -> { }));
        }
        assertNoPartials(root);
    }

    @Test
    void symlinksCannotRedirectInstallationAndExistingDirectoryIsPreserved(@TempDir Path root) throws Exception {
        Path folder = Files.createDirectory(root.resolve("voices"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        try {
            Files.createSymbolicLink(folder.resolve(".voice-downloads"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            org.junit.jupiter.api.Assumptions.abort("Platform does not support symlinks");
        }
        var installer = new VoiceModelInstaller(VoiceModelInstallerTest::ok);
        assertThrows(IOException.class, () -> installer.install(model(), folder, true, p -> { }));
        try (var files = Files.list(outside)) {
            assertEquals(0, files.count());
        }
        Files.delete(folder.resolve(".voice-downloads"));
        Path existing = Files.createDirectory(folder.resolve(ID));
        Files.writeString(existing.resolve("user-file.txt"), "keep me");
        assertThrows(IOException.class, () -> installer.install(model(), folder, true, p -> { }));
        assertEquals("keep me", Files.readString(existing.resolve("user-file.txt")));
    }

    private static VoiceCatalog.Model model() throws Exception {
        return withFiles(List.of(asset(ID + ".onnx", MODEL), asset(ID + ".onnx.json", CONFIG)));
    }

    private static VoiceCatalog.Model withFiles(List<VoiceCatalog.Asset> files) {
        return new VoiceCatalog.Model(ID, "TEST ONLY", "en_US", 2, Map.of("TEST_ONLY_A", 0, "TEST_ONLY_B", 1),
            22050, URI.create(PREFIX + "en/MODEL_CARD"), "TEST ONLY", "Not a real voice model", true, files);
    }

    private static VoiceCatalog.Asset asset(String name, byte[] bytes) throws Exception {
        return new VoiceCatalog.Asset("en/" + name, URI.create(PREFIX + "en/" + name), bytes.length, "MD5",
            HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes)));
    }

    private static VoiceModelInstaller.Response ok(URI uri) {
        byte[] bytes = uri.getPath().endsWith(".onnx") ? MODEL : CONFIG;
        return new VoiceModelInstaller.Response(200, null, bytes.length, new ByteArrayInputStream(bytes));
    }

    /** Mimics JDK 17 HTTP body reads that consume interrupts but unblock when the stream is closed. */
    private static final class StalledBody extends InputStream {
        final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);

        @Override public int read() throws IOException {
            return read(new byte[1], 0, 1);
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            entered.countDown();
            while (closed.getCount() != 0) {
                try {
                    closed.await();
                } catch (InterruptedException ignored) {
                    // Reproduce the underlying HTTP stream, not a correctly interruptible test fake.
                }
            }
            throw new IOException("Stream closed");
        }

        @Override public void close() {
            closed.countDown();
        }
    }

    private static void assertNoPartials(Path folder) throws IOException {
        try (var paths = Files.walk(folder)) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().endsWith(".part")));
        }
        Path staging = folder.resolve(".voice-downloads");
        if (Files.exists(staging)) {
            try (var files = Files.list(staging)) {
                assertEquals(0, files.count(), "Staging directories should be removed after failure/cancel");
            }
        }
    }
}
