package com.annaschneider.minecraft1.video.voice;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Opt-in, bounded downloads; a complete model directory becomes visible in one atomic rename. */
public final class VoiceModelInstaller {
    public record Progress(String modelId, long downloadedBytes, long totalBytes) { }

    public record Response(int status, URI location, long contentLength, InputStream body) implements AutoCloseable {
        @Override public void close() throws IOException { body.close(); }
    }

    @FunctionalInterface
    public interface Transport {
        Response open(URI uri) throws IOException, InterruptedException;
    }

    private static final Set<String> HOSTS = Set.of("huggingface.co", "cdn-lfs.huggingface.co",
        "cdn-lfs-us-1.huggingface.co", "cdn-lfs-eu-1.huggingface.co", "cdn-lfs.hf.co",
        "cdn-lfs-us-1.hf.co", "cdn-lfs-eu-1.hf.co", "cas-bridge.xethub.hf.co");
    private static final long MAX_MODEL_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(10);
    private final Transport transport;
    private final Duration transferTimeout;

    public VoiceModelInstaller() {
        transferTimeout = TRANSFER_TIMEOUT;
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(20)).build();
        transport = uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10)).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return new Response(response.statusCode(),
                response.headers().firstValue("Location").map(uri::resolve).orElse(null),
                response.headers().firstValueAsLong("Content-Length").orElse(-1), response.body());
        };
    }

    /** Tests inject transport, but all URL/path/integrity checks still apply. */
    public VoiceModelInstaller(Transport transport) { this(transport, TRANSFER_TIMEOUT); }

    public VoiceModelInstaller(Transport transport, Duration transferTimeout) {
        this.transport = java.util.Objects.requireNonNull(transport);
        this.transferTimeout = java.util.Objects.requireNonNull(transferTimeout);
        if (transferTimeout.isZero() || transferTimeout.isNegative() || transferTimeout.compareTo(TRANSFER_TIMEOUT) > 0) {
            throw new IllegalArgumentException("Body transfer timeout must be positive and at most ten minutes.");
        }
    }

    public Path install(VoiceCatalog.Model model, Path folder, boolean licenseAccepted, Consumer<Progress> progress)
        throws IOException, InterruptedException {
        if (!licenseAccepted || !model.downloadable()) {
            throw new IOException("Installation requires an available model and explicit acceptance of its license/restrictions.");
        }
        validateAssets(model);
        checkCancelled();
        Files.createDirectories(folder);
        Path root = folder.toRealPath();
        Path locks = safeDirectory(root, ".voice-download-locks");
        Path downloads = safeDirectory(root, ".voice-downloads");
        Path lockPath = locks.resolve(model.id() + ".lock");
        if (Files.isSymbolicLink(lockPath)) {
            throw new IOException("Download lock must not be a symbolic link.");
        }
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS)) {
            java.nio.channels.FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (java.nio.channels.OverlappingFileLockException ex) {
                throw new IOException("This shared model is already being installed. Retry after it finishes.", ex);
            }
            if (lock == null) {
                throw new IOException("This shared model is already being installed. Retry after it finishes.");
            }
            try (lock) {
                // Legacy manual packs need only model/config; verify an accompanying card when present.
                Path existing = findExisting(root, model);
                if (existing != null) {
                    progress.accept(new Progress(model.id(), model.downloadBytes(), model.downloadBytes()));
                    return existing;
                }
                Path target = root.resolve(model.id());
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("An incomplete or different model already exists at " + target
                        + ". Keep a backup and remove/rename it before retrying; it will not be overwritten.");
                }
                Path stage = Files.createTempDirectory(downloads, model.id() + "-");
                try {
                    long completed = 0;
                    for (VoiceCatalog.Asset asset : model.files()) {
                        Path partial = stage.resolve(fileName(asset.path()) + ".part");
                        long previous = completed;
                        download(asset, partial, count ->
                            progress.accept(new Progress(model.id(), previous + count, model.downloadBytes())));
                        checkCancelled();
                        Files.move(partial, stage.resolve(fileName(asset.path())), StandardCopyOption.ATOMIC_MOVE);
                        completed += asset.sizeBytes();
                    }
                    validatePack(stage, model);
                    checkCancelled();
                    // No replacement: failures leave the user's existing models untouched.
                    Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
                    return target;
                } finally {
                    deleteStage(stage);
                }
            }
        }
    }

    private static Path safeDirectory(Path root, String name) throws IOException {
        Path directory = root.resolve(name);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Download directory must not be a symbolic link.");
        }
        Files.createDirectories(directory);
        if (!directory.toRealPath().startsWith(root)) {
            throw new IOException("Download directory escapes the voices folder.");
        }
        return directory;
    }

    public static void validateUrl(URI uri) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || !HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))
            || uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("Untrusted model URL or redirect; only approved HTTPS model hosts are allowed.");
        }
    }

    private static String fileName(String path) throws IOException {
        if (path == null || !path.matches("[A-Za-z0-9_+-]+(?:/[A-Za-z0-9_.+-]+)+")
            || List.of(path.split("/")).contains("..") || List.of(path.split("/")).contains(".")) {
            throw new IOException("Invalid model artifact path.");
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void validateAssets(VoiceCatalog.Model model) throws IOException {
        Set<String> names = new HashSet<>();
        if (model.files().size() < 2 || model.files().size() > 3 || model.license().isBlank()) {
            throw new IOException("Model/config and reviewed license metadata are required.");
        }
        for (VoiceCatalog.Asset asset : model.files()) {
            String name = fileName(asset.path());
            if (!names.add(name) || !Set.of(model.id() + ".onnx", model.id() + ".onnx.json", "MODEL_CARD").contains(name)) {
                throw new IOException("Only the model, its config and model card may be downloaded.");
            }
            validateUrl(asset.url());
            String prefix = "/rhasspy/piper-voices/resolve/";
            String urlPath = asset.url().getPath();
            if (!"huggingface.co".equals(asset.url().getHost()) || asset.url().getQuery() != null
                || !urlPath.startsWith(prefix) || !urlPath.endsWith("/" + asset.path())) {
                throw new IOException("Artifacts must originate from the official Piper voices repository.");
            }
            String revision = urlPath.substring(prefix.length(), urlPath.length() - asset.path().length() - 1);
            if (!revision.matches("[a-f0-9]{40}") && !"v1.0.0".equals(revision)) {
                throw new IOException("Model download must use a pinned commit or the verified Piper v1.0.0 release.");
            }
            long limit = name.endsWith(".onnx") ? MAX_MODEL_BYTES : 1024 * 1024;
            if (asset.sizeBytes() <= 0 || asset.sizeBytes() > limit) {
                throw new IOException("Model artifact size is missing or exceeds the allowed limit.");
            }
            if (!Set.of("MD5", "SHA-256").contains(asset.hashAlgorithm())
                || asset.hash() == null || !asset.hash().matches(
                    "SHA-256".equals(asset.hashAlgorithm()) ? "[a-f0-9]{64}" : "[a-f0-9]{32}")) {
                throw new IOException("An authoritative model artifact checksum is required.");
            }
        }
        if (!names.contains(model.id() + ".onnx") || !names.contains(model.id() + ".onnx.json")) {
            throw new IOException("Both the model and config are required.");
        }
    }

    private void download(VoiceCatalog.Asset asset, Path partial, Consumer<Long> progress)
        throws IOException, InterruptedException {
        long deadline = System.nanoTime() + transferTimeout.toNanos();
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "voice-download-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            download(asset, partial, progress, reader, deadline);
        } finally {
            reader.shutdownNow();
        }
    }

    private void download(VoiceCatalog.Asset asset, Path partial, Consumer<Long> progress, ExecutorService reader,
                          long deadline) throws IOException, InterruptedException {
        URI uri = asset.url();
        for (int redirects = 0; redirects <= 5; redirects++) {
            checkCancelled();
            if (deadline - System.nanoTime() <= 0) {
                throw new IOException("Model body download timed out; nothing was installed.");
            }
            validateUrl(uri);
            try (Response response = transport.open(uri)) {
                if (Set.of(301, 302, 303, 307, 308).contains(response.status())) {
                    if (redirects == 5 || response.location() == null) {
                        throw new IOException("Too many redirects or missing redirect location.");
                    }
                    validateUrl(response.location());
                    uri = response.location();
                    continue;
                }
                if (response.status() != 200) {
                    throw new IOException("Model server returned HTTP " + response.status() + "; retry later.");
                }
                if (response.contentLength() >= 0 && response.contentLength() != asset.sizeBytes()) {
                    throw new IOException("Model download size does not match the verified manifest.");
                }
                MessageDigest digest = digest(asset.hashAlgorithm());
                long count = 0;
                byte[] buffer = new byte[64 * 1024];
                try (var output = Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW)) {
                    int read;
                    while ((read = readBody(response.body(), buffer, reader, deadline)) != -1) {
                        checkCancelled();
                        count += read;
                        if (count > asset.sizeBytes()) {
                            throw new IOException("Model download exceeds the verified size.");
                        }
                        output.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        progress.accept(count);
                    }
                }
                if (count != asset.sizeBytes() || !HexFormat.of().formatHex(digest.digest()).equals(asset.hash())) {
                    throw new IOException("Model download checksum/size mismatch; nothing was installed. Retry.");
                }
                return;
            }
        }
        throw new IOException("Model redirect limit exceeded.");
    }

    private static int readBody(InputStream input, byte[] buffer, ExecutorService reader, long deadline)
        throws IOException, InterruptedException {
        // JDK 17 HTTP body streams can swallow reader interrupts. Wait interruptibly on a separate read,
        // then close the response in download() on cancellation/timeout to unblock the actual stream.
        var read = reader.submit(() -> input.read(buffer));
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IOException("Model body download timed out; nothing was installed.");
            }
            return read.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            throw new IOException("Model body download timed out; nothing was installed.", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException failure) {
                throw failure;
            }
            throw new IOException("Could not read model download body.", ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } finally {
            if (!read.isDone()) {
                read.cancel(true);
            }
        }
    }

    private static MessageDigest digest(String algorithm) throws IOException {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("Checksum algorithm unavailable", ex);
        }
    }

    private static Path findExisting(Path root, VoiceCatalog.Model model) throws IOException, InterruptedException {
        Path managed = root.resolve(model.id());
        List<Path> candidates = new java.util.ArrayList<>();
        if (Files.exists(managed, LinkOption.NOFOLLOW_LINKS)) {
            candidates.add(managed);
        }
        candidates.add(root);
        try (var children = Files.list(root)) {
            children.filter(p -> !p.getFileName().toString().startsWith("."))
                .filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                .filter(p -> !p.equals(managed)).sorted().forEach(candidates::add);
        }
        Path damaged = null;
        for (Path directory : candidates) {
            if (Files.isSymbolicLink(directory)) {
                throw new IOException("Model directory must not be a symbolic link.");
            }
            if (!Files.exists(directory.resolve(model.id() + ".onnx"), LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            boolean matches = true;
            for (VoiceCatalog.Asset asset : model.files()) {
                Path file = directory.resolve(fileName(asset.path()));
                if ("MODEL_CARD".equals(file.getFileName().toString())
                    && !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Installed model artifact must not be a symbolic link.");
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != asset.sizeBytes()) {
                    matches = false;
                    break;
                }
                MessageDigest digest = digest(asset.hashAlgorithm());
                try (var input = Files.newInputStream(file)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        checkCancelled();
                        digest.update(buffer, 0, read);
                    }
                }
                if (!HexFormat.of().formatHex(digest.digest()).equals(asset.hash())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                validatePack(directory, model);
                return directory;
            }
            damaged = directory;
        }
        if (damaged != null) {
            throw new IOException("A different or damaged copy of this shared model exists at " + damaged
                + ". Back it up and remove/rename it before retrying.");
        }
        return null;
    }

    private static void validatePack(Path folder, VoiceCatalog.Model model) throws IOException {
        try {
            List<VoicePack> speakers = new VoicePackRegistry(Set.of(PiperTtsEngine.ID))
                .loadSpeakers(folder, folder.resolve(model.id() + ".onnx"));
            if (speakers.size() != model.numSpeakers()) {
                throw new IOException("Downloaded speaker count differs from the verified catalog.");
            }
            Map<Integer, String> identities = new java.util.HashMap<>();
            model.speakerIdMap().forEach((identity, id) -> identities.put(id, identity));
            for (VoicePack voice : speakers) {
                int id = voice.speakerId() == null ? 0 : voice.speakerId();
                if (!model.language().equals(voice.language()) || voice.sampleRate() != model.sampleRate()
                    || (model.numSpeakers() > 1 && !identities.getOrDefault(id, "").equals(voice.speakerIdentity()))) {
                    throw new IOException("Downloaded language/speaker identity differs from the verified catalog.");
                }
            }
        } catch (InvalidVoicePackException ex) {
            throw new IOException("Downloaded model/config is not a valid voice pack: " + ex.getMessage(), ex);
        }
    }

    private static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Voice download cancelled.");
        }
    }

    private static void deleteStage(Path stage) throws IOException {
        if (!Files.exists(stage)) {
            return;
        }
        try (var files = Files.walk(stage)) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
