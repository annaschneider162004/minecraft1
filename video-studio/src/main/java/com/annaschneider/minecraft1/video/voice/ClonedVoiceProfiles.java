package com.annaschneider.minecraft1.video.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Local reference-audio profiles, published only after the cloning engine successfully speaks a preview. */
public final class ClonedVoiceProfiles {
    public static final String SUFFIX = ".cloned.json";
    public static final long MAX_SAMPLE_BYTES = 20L * 1024 * 1024;
    public static final String CHECKING = "Đang kiểm tra mẫu WAV...";
    public static final String CREATING = "Đang tạo hồ sơ giọng đọc...";
    public static final String READY = "Hồ sơ giọng đọc đã sẵn sàng";
    public static final String FAILED = "Tạo hồ sơ giọng đọc thất bại: ";
    private final TtsEngine engine;

    public ClonedVoiceProfiles(TtsEngine engine) {
        this.engine = engine;
    }

    /** Accepts 6–60 seconds of readable, uncompressed mono/stereo PCM WAV, up to 20 MB. */
    public static void validateSample(Path sample) throws NarrationException {
        try {
            if (!sample.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav")) {
                throw new NarrationException("Định dạng mẫu không được hỗ trợ. Chọn WAV PCM (.wav); âm thanh mẫu không phải gói giọng .onnx.");
            }
            if (!Files.isRegularFile(sample) || !Files.isReadable(sample)) {
                throw new NarrationException("Không thể đọc tệp âm thanh mẫu.");
            }
            long size = Files.size(sample);
            if (size <= 44 || size > MAX_SAMPLE_BYTES) {
                throw new NarrationException("Mẫu phải chứa âm thanh và không lớn hơn 20 MB.");
            }
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(sample.toFile())) {
                AudioFormat format = audio.getFormat();
                if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                    || format.getSampleSizeInBits() != 16 || format.getChannels() < 1 || format.getChannels() > 2
                    || format.getSampleRate() < 8000 || format.getSampleRate() > 96000
                    || format.getFrameSize() != 2 * format.getChannels() || audio.getFrameLength() <= 0) {
                    throw new NarrationException("Dùng WAV PCM 16-bit, mono hoặc stereo, tần số 8–96 kHz.");
                }
                double seconds = audio.getFrameLength() / (double) format.getFrameRate();
                if (!Double.isFinite(seconds) || seconds < 6 || seconds > 60) {
                    throw new NarrationException("Mẫu WAV phải dài từ 6 đến 60 giây.");
                }
                byte[] buffer = new byte[8192];
                long bytes = 0;
                int count;
                while ((count = audio.read(buffer)) != -1) {
                    bytes += count;
                    if (bytes > MAX_SAMPLE_BYTES) {
                        throw new NarrationException("Âm thanh mẫu sau giải mã lớn hơn 20 MB.");
                    }
                }
                if (bytes != audio.getFrameLength() * format.getFrameSize()) {
                    throw new NarrationException("Âm thanh mẫu bị cắt cụt hoặc không đọc được.");
                }
            }
        } catch (IOException | UnsupportedAudioFileException ex) {
            throw new NarrationException("Không thể đọc âm thanh WAV của mẫu: " + ex.getMessage(), ex);
        }
    }

    public VoicePack create(Path folder, Path sample, String name, Consumer<String> progress) throws NarrationException {
        if (engine.unavailableReason().isPresent()) {
            throw new NarrationException(engine.unavailableReason().get());
        }
        String label = name == null ? "" : name.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").strip();
        if (label.isBlank() || label.length() > 60) {
            throw new NarrationException("Đặt tên hồ sơ giọng đọc (1–60 ký tự).");
        }
        progress.accept(CHECKING);
        validateSample(sample);
        String id = "clone-" + UUID.randomUUID();
        Path reference = folder.resolve(id + ".sample.wav");
        Path metadata = folder.resolve(id + SUFFIX);
        Path preview = null;
        boolean ready = false;
        try {
            Files.createDirectories(folder);
            Files.copy(sample, reference);
            validateSample(reference);
            VoicePack voice = pack(id, label, reference, metadata);
            progress.accept(CREATING);
            preview = Files.createTempFile("architect-clone-preview-", ".wav");
            engine.synthesize(voice, Narrator.previewText("en"), preview);
            WavInfo.seconds(preview);
            JsonObject json = new JsonObject();
            json.addProperty("name", label);
            json.addProperty("engine", XttsTtsEngine.ID);
            json.addProperty("language", "en");
            Files.writeString(metadata, json.toString());
            ready = true;
            progress.accept(READY);
            return voice;
        } catch (IOException ex) {
            throw new NarrationException("Không thể lưu hồ sơ giọng đọc: " + ex.getMessage(), ex);
        } finally {
            if (!ready) {
                delete(metadata);
                delete(reference);
            }
            if (preview != null) {
                delete(preview);
            }
        }
    }

    public VoiceDiscovery discover(Path folder) {
        List<VoicePack> voices = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return new VoiceDiscovery(folder, voices, problems);
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path metadata : files.filter(p -> p.getFileName().toString().endsWith(SUFFIX)).sorted().limit(200).toList()) {
                try {
                    String fileName = metadata.getFileName().toString();
                    String id = fileName.substring(0, fileName.length() - SUFFIX.length());
                    if (!id.matches("clone-[a-f0-9-]{36}") || Files.size(metadata) > 4096
                        || !metadata.toRealPath().startsWith(folder.toRealPath())) {
                        throw new NarrationException("Thông tin hồ sơ không hợp lệ.");
                    }
                    JsonObject json = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                    if (!XttsTtsEngine.ID.equals(json.get("engine").getAsString())
                        || !"en".equals(json.get("language").getAsString())) {
                        throw new NarrationException("Backend hoặc ngôn ngữ clone không được hỗ trợ; hiện chỉ hỗ trợ XTTS tiếng Anh.");
                    }
                    String name = json.get("name").getAsString();
                    if (name.isBlank() || name.length() > 60 || name.matches("(?s).*[\\p{Cntrl}\\p{Cf}].*")) {
                        throw new NarrationException("Tên hồ sơ giọng đọc không hợp lệ.");
                    }
                    Path sample = folder.resolve(id + ".sample.wav");
                    if (!sample.toRealPath().startsWith(folder.toRealPath())) {
                        throw new NarrationException("Mẫu liên kết ra ngoài thư mục giọng đọc (outside voices folder).");
                    }
                    validateSample(sample);
                    voices.add(pack(id, name, sample, metadata));
                } catch (IOException | NarrationException | RuntimeException ex) {
                    problems.add("Bỏ qua hồ sơ giọng đọc " + metadata.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            problems.add("Không thể đọc hồ sơ giọng cục bộ: " + ex.getMessage());
        }
        return new VoiceDiscovery(folder, voices, problems);
    }

    private static VoicePack pack(String id, String name, Path reference, Path metadata) {
        return new VoicePack(id, name + " [cloned]", "en", XttsTtsEngine.ID, reference.toAbsolutePath(),
            metadata.toAbsolutePath(), 24000, "Local voice cloned from sample audio");
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; an unpublished sample is never listed as a voice.
        }
    }
}
