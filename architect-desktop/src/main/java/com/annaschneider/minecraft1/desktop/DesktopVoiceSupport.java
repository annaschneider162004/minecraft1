package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.ExportMode;
import com.annaschneider.minecraft1.video.Storyboard;
import com.annaschneider.minecraft1.video.voice.VoiceCatalogEntry;
import com.annaschneider.minecraft1.video.voice.VoiceFilter;
import com.annaschneider.minecraft1.video.voice.VoiceDropInput;
import com.annaschneider.minecraft1.video.voice.VoiceInstallException;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class DesktopVoiceSupport {
    static final String DROP_HINT = "Kéo thả 1 WAV PCM (6–60 giây) để nhân bản giọng tiếng Anh, hoặc cặp .onnx + .onnx.json để nhập gói giọng.";
    static final String ENGLISH_SAMPLE = "On a quiet island, we begin with a single block. Stone by stone, a new kingdom rises. "
        + "We build strong walls, open gates, and a bright courtyard. From the first foundation to the tallest tower, "
        + "every detail tells a story. Welcome to our Minecraft adventure.";
    static final String VIETNAMESE_REFERENCE = "Trên hòn đảo yên bình, chúng ta bắt đầu từ một khối đá. "
        + "Từng viên gạch tạo nên một vương quốc mới. Những bức tường vững chắc, cánh cổng rộng mở và sân vườn "
        + "rực rỡ kể lại hành trình xây dựng. Hãy cùng khám phá công trình Minecraft của chúng ta.";
    static final String SAMPLE_GUIDANCE = "Đọc tự nhiên trong phòng yên tĩnh, không nhạc nền; thu WAV PCM 16-bit, "
        + "6–60 giây, tối đa 20 MB. XTTS v2 hiện chỉ nhân bản/thuyết minh tiếng Anh, không hỗ trợ nhân bản tiếng Việt.";

    private DesktopVoiceSupport() { }

    static String selectionAfterImport(String currentId, String importedId, boolean selectImported) {
        return selectImported ? importedId : currentId;
    }

    static VoiceDropInput classifyFiles(List<Path> paths, boolean packOnly) throws VoiceInstallException {
        VoiceDropInput input = VoiceDropInput.classify(paths);
        if (packOnly && input.kind() != VoiceDropInput.Kind.PIPER_PACK) {
            throw new VoiceInstallException("Nhập gói giọng cần .onnx và .onnx.json. Để nhân bản WAV, dùng Nhân bản giọng từ WAV.");
        }
        return input;
    }

    static String label(Object value) {
        if (value instanceof ExportMode mode) {
            return switch (mode) {
                case RECORD_ONLY -> "Chỉ ghi hình";
                case NARRATE_ONLY -> "Chỉ thuyết minh";
                case AUTO_EXPORT -> "Tự xuất khi ghi hình và thuyết minh xong";
            };
        }
        if (value instanceof VoiceFilter.State state) {
            return switch (state) {
                case ALL -> "Tất cả giọng";
                case INSTALLED -> "Đã cài";
                case DOWNLOADABLE -> "Có thể tải";
                case CLONED -> "Đã nhân bản";
            };
        }
        if (value instanceof VoiceCatalogEntry.Source source) {
            return switch (source) {
                case INSTALLED_PACK -> "gói đã cài";
                case CLONED -> "giọng nhân bản";
                case CATALOG -> "danh mục đã xác minh";
            };
        }
        if (value instanceof VoiceCatalogEntry entry) {
            return entry.displayName() + (entry.speaker() == null ? "" : " (người nói " + (entry.speaker().index() + 1)
                + "/" + entry.speakerCount() + ")") + " — " + entry.language() + ", " + entry.engine() + " ["
                + (entry.installed() ? (entry.cloned() ? "đã nhân bản" : "đã cài") : "có thể tải, "
                + VoiceCatalogEntry.megabytes(entry.download().totalBytes())) + "]";
        }
        return String.valueOf(value);
    }

    static String describe(Storyboard story) {
        StringBuilder text = new StringBuilder(story.title()).append('\n');
        text.append(String.format(Locale.ROOT, "%d cảnh, khoảng %.0f giây — tạo bởi %s%n",
            story.scenes().size(), story.totalSeconds(), story.generator()));
        for (var scene : story.scenes()) {
            String kind = switch (scene.kind().name()) {
                case "INTRO" -> "mở đầu";
                case "OUTRO" -> "kết thúc";
                case "TIMELAPSE" -> "tua nhanh";
                default -> "công trình";
            };
            text.append(String.format(Locale.ROOT, "%n%d. %s [%s, %.1f giây, tiến độ %.0f%%]%n",
                scene.index() + 1, scene.title(), kind, scene.durationSeconds(), scene.fromProgress() * 100));
            if (!scene.narration().isBlank()) {
                text.append("   ").append(scene.narration()).append('\n');
            }
        }
        return text.toString();
    }
}
