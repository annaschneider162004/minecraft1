package com.annaschneider.minecraft1.video.voice;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Installs one verified catalog voice, only when the user asks for it. Files are downloaded over HTTPS into a hidden
 * staging folder inside the voices folder, checked against the catalog size and checksum, validated as a voice pack
 * (including its speaker list), and then moved into place in one atomic step. On any failure or cancellation the
 * staging folder is removed, so the voices folder never contains a half-installed voice.
 */
public final class VoiceInstaller {
    public static final String STAGING_PREFIX = ".install-";
    private static final String PART_SUFFIX = ".part";

    /** Opens a download; the stream is closed by the installer. */
    @FunctionalInterface
    public interface Downloader {
        InputStream open(URI url) throws IOException, InterruptedException;
    }

    private final Downloader downloader;
    private final VoicePackRegistry registry;

    public VoiceInstaller(Downloader downloader) {
        this.downloader = downloader;
        this.registry = new VoicePackRegistry(Set.of(PiperTtsEngine.ID));
    }

    /** Downloads with the JDK HTTP client (HTTPS only, redirects never downgrade to HTTP). */
    public static VoiceInstaller https() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20)).build();
        return new VoiceInstaller(url -> {
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(30))
                .header("User-Agent", "MinecraftArchitect-VideoStudio").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            if (!"https".equalsIgnoreCase(response.uri().getScheme())) {
                response.body().close();
                throw new IOException("refusing a non-HTTPS download from " + response.uri().getHost());
            }
            return response.body();
        });
    }

    /** @return the folder the voice was installed into ({@code <voices>/<model id>}) */
    public Path install(DownloadableVoice voice, Path voicesFolder, Consumer<String> progress) throws VoiceInstallException {
        for (DownloadableVoice.CatalogFile file : voice.files()) {
            try {
                URI base = VoiceCatalogSource.baseUrl(voice.baseUrl().toString());
                if (!voice.url(file).toString().startsWith(base.toString())) {
                    throw new IllegalArgumentException(file.path());
                }
            } catch (IllegalArgumentException ex) {
                throw new VoiceInstallException("Refusing to download " + voice.modelId() + " from an unverified address: " + ex.getMessage());
            }
        }
        Path target = voicesFolder.resolve(voice.modelId());
        Path staging = null;
        boolean installed = false;
        try {
            Files.createDirectories(voicesFolder);
            if (Files.exists(target) || registry.discover(voicesFolder).voices().stream()
                    .anyMatch(pack -> pack.id().equals(voice.modelId()))) {
                throw new VoiceInstallException("Voice " + voice.modelId() + " is already in the voices folder (" + target
                    + "). Click Refresh to list it.");
            }
            staging = Files.createTempDirectory(voicesFolder, STAGING_PREFIX);
            int number = 0;
            for (DownloadableVoice.CatalogFile file : voice.files()) {
                number++;
                progress.accept(String.format(Locale.ROOT, "Downloading %s (%d of %d, %s)...", file.fileName(), number,
                    voice.files().size(), VoiceCatalogEntry.megabytes(file.size())));
                download(voice.url(file), file, staging.resolve(file.fileName() + PART_SUFFIX));
            }
            progress.accept("Checking " + voice.modelId() + "...");
            for (DownloadableVoice.CatalogFile file : voice.files()) {
                Files.move(staging.resolve(file.fileName() + PART_SUFFIX), staging.resolve(file.fileName()));
            }
            validate(voice, staging);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new VoiceInstallException("Cannot install " + voice.modelId() + " atomically in " + voicesFolder, ex);
            }
            installed = true;
            progress.accept("Installed " + voice.modelId() + " in " + target);
            return target;
        } catch (InterruptedIOException ex) {
            throw new VoiceInstallException("Installing " + voice.modelId() + " was cancelled; nothing was installed.", ex);
        } catch (IOException ex) {
            throw new VoiceInstallException("Could not install " + voice.modelId() + ": " + ex.getMessage()
                + ". Nothing was installed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new VoiceInstallException("Installing " + voice.modelId() + " was cancelled; nothing was installed.", ex);
        } finally {
            if (!installed && staging != null) {
                deleteTree(staging);
            }
        }
    }

    private void download(URI url, DownloadableVoice.CatalogFile file, Path part)
        throws IOException, InterruptedException, VoiceInstallException {
        MessageDigest digest;
        try {
            // Integrity check against the checksum published with the model, alongside the exact size and HTTPS.
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            throw new VoiceInstallException("This Java runtime cannot verify downloads (MD5 missing).", ex);
        }
        long total = 0;
        try (InputStream in = downloader.open(url); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("cancelled");
                }
                total += count;
                if (total > file.size()) {
                    throw new VoiceInstallException(file.fileName() + " is larger than the verified size ("
                        + file.size() + " bytes). Nothing was installed.");
                }
                digest.update(buffer, 0, count);
                out.write(buffer, 0, count);
            }
        }
        if (total != file.size()) {
            throw new VoiceInstallException(file.fileName() + " is incomplete (" + total + " of " + file.size()
                + " bytes). Nothing was installed.");
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(file.md5())) {
            throw new VoiceInstallException(file.fileName() + " does not match the verified checksum. Nothing was installed.");
        }
    }

    private void validate(DownloadableVoice voice, Path staging) throws VoiceInstallException {
        try {
            VoicePack pack = registry.load(staging, staging.resolve(voice.modelId() + VoicePackRegistry.MODEL_EXTENSION));
            List<VoiceSpeaker> speakers = PiperSpeakers.read(pack.config());
            if (!speakers.equals(voice.speakers())) {
                throw new VoiceInstallException("The downloaded config of " + voice.modelId() + " has " + Math.max(1, speakers.size())
                    + " speaker(s), but the catalog lists " + Math.max(1, voice.speakers().size()) + ". Nothing was installed.");
            }
        } catch (InvalidVoicePackException ex) {
            throw new VoiceInstallException("The downloaded voice " + voice.modelId() + " is not valid: " + ex.getMessage()
                + ". Nothing was installed.");
        }
    }

    private static void deleteTree(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException ignored) {
            // Best effort: a leftover hidden staging folder is never listed as a voice.
        }
    }
}
