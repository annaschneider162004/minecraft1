package com.annaschneider.minecraft1.video.voice;

import com.annaschneider.minecraft1.video.VideoExportException;
import com.annaschneider.minecraft1.video.render.FfmpegTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Processes into a separate WAV so failed FFmpeg runs cannot replace synthesized audio. */
final class NarrationAudioProcessor {
    private static final String MISSING_MESSAGE = "Không tìm thấy FFmpeg để áp dụng tốc độ hoặc hiệu ứng giọng đọc. "
        + "Hãy cài đặt FFmpeg và cấu hình trong Video Studio > Công cụ, hoặc đặt tốc độ 1.0 và tắt hiệu ứng.";

    private final NarrationAudioOptions options;
    private final Supplier<Optional<FfmpegTool>> ffmpeg;

    NarrationAudioProcessor(NarrationAudioOptions options, Supplier<Optional<FfmpegTool>> ffmpeg) {
        this.options = Objects.requireNonNull(options, "options");
        this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
    }

    Optional<String> unavailableReason() {
        return options.requiresProcessing() && ffmpeg.get().isEmpty()
            ? Optional.of(MISSING_MESSAGE) : Optional.empty();
    }

    void process(Path wav) throws NarrationException {
        if (!options.requiresProcessing()) {
            return;
        }
        FfmpegTool tool = ffmpeg.get().orElseThrow(() -> new NarrationException(MISSING_MESSAGE));
        String filters = "atempo=" + Double.toString(options.speed());
        if (options.effect() == NarrationAudioOptions.Effect.SOFT_ECHO) {
            filters += ",aecho=0.8:0.9:60:0.2";
        }
        try {
            Path processed = Files.createTempFile(wav.toAbsolutePath().getParent(), "narration-audio-", ".wav");
            try {
                tool.run(List.of("-i", wav.toString(), "-vn", "-af", filters, "-c:a", "pcm_s16le",
                    "-f", "wav", processed.toString()), "xử lý tốc độ và hiệu ứng giọng đọc");
                WavInfo.seconds(processed);
                Files.move(processed, wav, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(processed);
            }
        } catch (IOException | VideoExportException ex) {
            throw new NarrationException("Không thể xử lý tốc độ hoặc hiệu ứng giọng đọc: " + ex.getMessage(), ex);
        }
    }
}
