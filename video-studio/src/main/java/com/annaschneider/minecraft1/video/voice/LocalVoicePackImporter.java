package com.annaschneider.minecraft1.video.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Copies a local Piper pair into a hidden staging folder, then publishes the model atomically after its companions.
 * Structural/config validation is not a guarantee that an ONNX model can run in Piper.
 */
public final class LocalVoicePackImporter {
    @FunctionalInterface
    interface Publisher {
        void publish(Path model, Path destination) throws IOException;
    }

    private static final Object PUBLISH_LOCK = new Object();
    private final VoicePackRegistry registry = new VoicePackRegistry(Set.of(PiperTtsEngine.ID));
    private final Publisher publisher;

    public LocalVoicePackImporter() {
        this((model, destination) -> Files.move(model, destination, StandardCopyOption.ATOMIC_MOVE));
    }

    LocalVoicePackImporter(Publisher publisher) {
        this.publisher = publisher;
    }

    /** A null config discovers only the exact {@code <model>.json} sibling. Source files are never changed. */
    public VoicePack importPack(Path model, Path config, Path voicesFolder, String name, String description)
        throws VoiceInstallException {
        VoiceDropInput input = VoiceDropInput.classify(config == null
            ? model == null ? List.of() : List.of(model)
            : model == null ? List.of(config) : List.of(model, config));
        if (input.kind() != VoiceDropInput.Kind.PIPER_PACK || model == null) {
            throw new VoiceInstallException("Nhập gói Piper cần mô hình .onnx và cấu hình .onnx.json, không phải tệp âm thanh.");
        }
        if (voicesFolder == null) {
            throw new VoiceInstallException("Chưa chọn thư mục lưu giọng nói.");
        }
        String fileName = input.model().getFileName().toString();
        String id = fileName.substring(0, fileName.length() - VoicePackRegistry.MODEL_EXTENSION.length());
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,99}")) {
            throw new VoiceInstallException("Tên mô hình chỉ được chứa chữ, số, dấu chấm, gạch dưới, + và - (tối đa 100 ký tự).");
        }
        Path staging = null;
        Path target = null;
        boolean reserved = false;
        boolean configTransferred = false;
        boolean metadataTransferred = false;
        boolean published = false;
        try {
            Files.createDirectories(voicesFolder);
            Path root = voicesFolder.toRealPath();
            target = root.resolve(id);
            rejectCollision(root, id);
            staging = Files.createTempDirectory(root, VoiceInstaller.STAGING_PREFIX + "local-");
            Path stagedModel = staging.resolve(fileName);
            Path stagedConfig = staging.resolve(fileName + ".json");
            copyBounded(input.model(), stagedModel, VoicePackRegistry.MAX_MODEL_BYTES);
            copyBounded(input.config(), stagedConfig, VoicePackRegistry.MAX_JSON_BYTES);
            rejectAudio(stagedModel);
            JsonObject settings = VoicePackRegistry.readJson(stagedConfig);
            JsonElement audio = settings.get("audio");
            validateInteger(audio != null && audio.isJsonObject() ? audio.getAsJsonObject() : null,
                "sample_rate", 8000, 96000, true);
            validateInteger(settings, "num_speakers", 1, PiperSpeakers.MAX_SPEAKERS, false);
            int speakerCount = settings.has("num_speakers") ? settings.get("num_speakers").getAsInt() : 1;
            JsonElement map = settings.get("speaker_id_map");
            if (map != null) {
                if (!map.isJsonObject()) {
                    throw new VoiceInstallException("Cấu hình Piper có speaker_id_map không hợp lệ.");
                }
                for (var entry : map.getAsJsonObject().entrySet()) {
                    validateInteger(map.getAsJsonObject(), entry.getKey(), 0, speakerCount - 1, true);
                }
            }
            PiperSpeakers.parse(settings);
            JsonObject metadata = new JsonObject();
            metadata.addProperty("engine", PiperTtsEngine.ID);
            String label = cleanMetadata(name, 60);
            String detail = cleanMetadata(description, 200);
            if (!label.isBlank()) {
                metadata.addProperty("name", label);
            }
            if (!detail.isBlank()) {
                metadata.addProperty("description", detail);
            }
            Files.writeString(staging.resolve(id + VoicePackRegistry.METADATA_SUFFIX), metadata.toString(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            VoicePack validated = registry.load(staging, stagedModel);
            synchronized (PUBLISH_LOCK) {
                rejectCollision(root, id);
                // Reserve with CREATE semantics: ATOMIC_MOVE alone can replace an existing empty directory.
                Files.createDirectory(target);
                reserved = true;
                Files.move(stagedConfig, target.resolve(fileName + ".json"));
                configTransferred = true;
                Files.move(staging.resolve(id + VoicePackRegistry.METADATA_SUFFIX),
                    target.resolve(id + VoicePackRegistry.METADATA_SUFFIX));
                metadataTransferred = true;
                // The registry discovers models, so publishing the model last exposes only complete packs.
                publisher.publish(stagedModel, target.resolve(fileName));
                published = true;
            }
            return new VoicePack(validated.id(), validated.name(), validated.language(), validated.engine(),
                target.resolve(fileName), target.resolve(fileName + ".json"), validated.sampleRate(), validated.description());
        } catch (InvalidVoicePackException | IOException | RuntimeException ex) {
            throw new VoiceInstallException("Không thể nhập gói giọng nói: mô hình/cấu hình không hợp lệ hoặc không thể lưu. "
                + "Chưa nhập gói nào.", ex);
        } finally {
            if (staging != null) {
                deleteStaging(staging);
            }
            if (!published) {
                if (reserved) {
                    try {
                        if (configTransferred) {
                            Files.deleteIfExists(target.resolve(fileName + ".json"));
                        }
                        if (metadataTransferred) {
                            Files.deleteIfExists(target.resolve(id + VoicePackRegistry.METADATA_SUFFIX));
                        }
                        Files.deleteIfExists(target);
                    } catch (IOException ignored) {
                        // Never delete files created by someone else in the reserved directory.
                    }
                }
            }
        }
    }

    private static void rejectCollision(Path root, String id) throws IOException, VoiceInstallException {
        String lowerId = id.toLowerCase(Locale.ROOT);
        try (Stream<Path> files = Files.walk(root, 2)) {
            boolean exists = files.filter(p -> !p.equals(root))
                .filter(p -> !root.relativize(p).getName(0).toString().startsWith(VoiceInstaller.STAGING_PREFIX))
                .anyMatch(p -> {
                    String value = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return value.equals(lowerId) || value.equals(lowerId + VoicePackRegistry.MODEL_EXTENSION)
                        || value.equals(lowerId + VoicePackRegistry.CONFIG_SUFFIX)
                        || value.equals(lowerId + ".json")
                        || value.equals(lowerId + VoicePackRegistry.METADATA_SUFFIX)
                        || value.equals(lowerId + ClonedVoiceProfiles.SUFFIX)
                        || value.equals(lowerId + ".sample.wav");
                });
            if (exists) {
                throw new VoiceInstallException("Giọng nói hoặc voice profile '" + id + "' đã tồn tại; không ghi đè.");
            }
        }
    }

    private static void copyBounded(Path source, Path destination, long limit) throws IOException, VoiceInstallException {
        long size = Files.size(source);
        if (size == 0 || size > limit) {
            throw new VoiceInstallException("Tệp giọng nói rỗng hoặc vượt quá giới hạn dung lượng: " + source.getFileName());
        }
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("cancelled");
                }
                total += count;
                if (total > limit) {
                    throw new VoiceInstallException("Tệp giọng nói vượt quá giới hạn dung lượng: " + source.getFileName());
                }
                out.write(buffer, 0, count);
            }
            if (total == 0 || total != size) {
                throw new VoiceInstallException("Tệp giọng nói đã thay đổi trong khi nhập; hãy thử lại.");
            }
        }
    }

    private static void validateInteger(JsonObject object, String key, int min, int max, boolean required)
        throws VoiceInstallException {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null && !required) {
            return;
        }
        try {
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                int number = value.getAsBigDecimal().intValueExact();
                if (number >= min && number <= max) {
                    return;
                }
            }
        } catch (ArithmeticException | NumberFormatException ignored) {
            // Fractional and overflowing numbers are not valid Piper indices/rates.
        }
        throw new VoiceInstallException("Cấu hình Piper có " + key + " không hợp lệ.");
    }

    private static String cleanMetadata(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        return value.length() > max ? value.substring(0, max).strip() : value;
    }

    private static void rejectAudio(Path model) throws IOException, VoiceInstallException {
        byte[] header;
        try (InputStream in = Files.newInputStream(model)) {
            header = in.readNBytes(16);
        }
        String magic = new String(header, StandardCharsets.ISO_8859_1);
        boolean audio = magic.startsWith("RIFF") || magic.startsWith("RIFX") || magic.startsWith("RF64")
            || magic.startsWith("OggS") || magic.startsWith("fLaC") || magic.startsWith("ID3")
            || magic.startsWith("FORM") || magic.startsWith(".snd")
            || header.length >= 8 && magic.substring(4, 8).equals("ftyp")
            || header.length >= 2 && (header[0] & 0xff) == 0xff && (header[1] & 0xe0) == 0xe0;
        if (audio) {
            throw new VoiceInstallException("Tệp .onnx là tệp âm thanh đã đổi tên, không phải mô hình giọng nói Piper.");
        }
    }

    private static void deleteStaging(Path staging) {
        try (Stream<Path> files = Files.walk(staging)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // A leftover .install-* directory stays invisible to the voice registry.
        }
    }
}
