package com.annaschneider.minecraft1.video.voice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** A local file selection; classification does not decode WAV or verify ONNX runtime compatibility. */
public record VoiceDropInput(Kind kind, Path model, Path config, Path sample) {
    public enum Kind { WAV_SAMPLE, PIPER_PACK }

    public static VoiceDropInput classify(List<Path> paths) throws VoiceInstallException {
        if (paths == null || paths.isEmpty() || paths.size() > 2 || paths.stream().anyMatch(p -> p == null)) {
            throw new VoiceInstallException("Chọn một tệp WAV hoặc một cặp .onnx và .onnx.json cùng tên.");
        }
        for (Path path : paths) {
            requireFile(path);
        }
        if (paths.size() == 1 && fileName(paths.get(0)).toLowerCase(Locale.ROOT).endsWith(".wav")) {
            return new VoiceDropInput(Kind.WAV_SAMPLE, null, null, paths.get(0));
        }
        Path model = null;
        Path config = null;
        for (Path path : paths) {
            String name = fileName(path);
            if (name.endsWith(VoicePackRegistry.MODEL_EXTENSION) && model == null) {
                model = path;
            } else if (name.endsWith(VoicePackRegistry.CONFIG_SUFFIX) && config == null) {
                config = path;
            } else {
                throw new VoiceInstallException("Không trộn WAV với gói giọng nói; chỉ hỗ trợ .wav, .onnx và .onnx.json.");
            }
        }
        if (model == null) {
            String name = fileName(config);
            model = config.resolveSibling(name.substring(0, name.length() - ".json".length()));
        }
        if (config == null) {
            config = model.resolveSibling(fileName(model) + ".json");
        }
        if (!(fileName(model) + ".json").equals(fileName(config))) {
            throw new VoiceInstallException("Tệp cấu hình phải có đúng tên mô hình cộng .json (ví dụ voice.onnx.json).");
        }
        requireFile(model);
        requireFile(config);
        return new VoiceDropInput(Kind.PIPER_PACK, model, config, null);
    }

    private static String fileName(Path path) throws VoiceInstallException {
        if (path.getFileName() == null) {
            throw new VoiceInstallException("Đường dẫn không phải là tệp giọng nói.");
        }
        return path.getFileName().toString();
    }

    private static void requireFile(Path path) throws VoiceInstallException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new VoiceInstallException("Không đọc được tệp giọng nói: " + path.getFileName());
        }
    }
}
