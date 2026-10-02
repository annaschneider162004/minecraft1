package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.ExportFlow;
import com.annaschneider.minecraft1.video.ExportMode;
import com.annaschneider.minecraft1.video.story.OllamaClient;
import com.annaschneider.minecraft1.video.voice.VoiceFolders;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.prefs.Preferences;

/** Video Studio choices: tool paths, folders, local AI, the selected voice and export mode (kept in its own preferences node). */
final class VideoStudioSettings {
    static final int DEFAULT_RECORD_SECONDS = 120;
    static final int MIN_RECORD_SECONDS = 5;

    private final Preferences preferences;

    VideoStudioSettings() {
        this(Preferences.userNodeForPackage(VideoStudioSettings.class).node("video-studio"));
    }

    VideoStudioSettings(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** Explicit ffmpeg path (file or folder); blank = search {@code ARCHITECT_FFMPEG} and PATH. */
    String ffmpegPath() {
        return preferences.get("ffmpegPath", "");
    }

    void setFfmpegPath(String value) {
        putOrRemove("ffmpegPath", value);
    }

    /** Explicit piper path (file or folder); blank = search {@code ARCHITECT_PIPER}, the voices folder and PATH. */
    String piperPath() {
        return preferences.get("piperPath", "");
    }

    void setPiperPath(String value) {
        putOrRemove("piperPath", value);
    }

    String cloningPath() {
        return preferences.get("cloningPath", "");
    }

    void setCloningPath(String value) {
        putOrRemove("cloningPath", value);
    }

    String cloningModelFolder() {
        return preferences.get("cloningModelFolder", "");
    }

    void setCloningModelFolder(String value) {
        putOrRemove("cloningModelFolder", value);
    }

    Path voicesFolder() {
        return pathOr("voicesFolder", VoiceFolders.defaultVoicesFolder());
    }

    void setVoicesFolder(String value) {
        putOrRemove("voicesFolder", value);
    }

    Path outputFolder() {
        return pathOr("outputFolder", defaultOutputFolder());
    }

    void setOutputFolder(String value) {
        putOrRemove("outputFolder", value);
    }

    boolean useLocalAi() {
        return preferences.getBoolean("useLocalAi", false);
    }

    void setUseLocalAi(boolean value) {
        preferences.putBoolean("useLocalAi", value);
    }

    String ollamaUrl() {
        return preferences.get("ollamaUrl", OllamaClient.DEFAULT_URL);
    }

    void setOllamaUrl(String value) {
        putOrRemove("ollamaUrl", value);
    }

    String ollamaModel() {
        return preferences.get("ollamaModel", OllamaClient.DEFAULT_MODEL);
    }

    void setOllamaModel(String value) {
        putOrRemove("ollamaModel", value);
    }

    /** Id of the selected voice; blank = no narration. */
    String voiceId() {
        return preferences.get("voiceId", "");
    }

    void setVoiceId(String value) {
        putOrRemove("voiceId", value);
    }

    /** Mode of the Start button; Auto-export when both complete by default. */
    ExportMode exportMode() {
        try {
            return ExportMode.valueOf(preferences.get("exportMode", ExportMode.AUTO_EXPORT.name()));
        } catch (IllegalArgumentException ex) {
            return ExportMode.AUTO_EXPORT;
        }
    }

    void setExportMode(ExportMode mode) {
        preferences.put("exportMode", (mode == null ? ExportMode.AUTO_EXPORT : mode).name());
    }

    /** How long Record only / Auto-export record the screen. */
    int recordSeconds() {
        int seconds = preferences.getInt("recordSeconds", DEFAULT_RECORD_SECONDS);
        return Math.max(MIN_RECORD_SECONDS, Math.min((int) ExportFlow.MAX_RECORD_SECONDS, seconds));
    }

    void setRecordSeconds(int seconds) {
        preferences.putInt("recordSeconds", seconds);
    }

    static Path defaultOutputFolder() {
        return Path.of(System.getProperty("user.home", "."), "Videos", "Minecraft Architect");
    }

    private Path pathOr(String key, Path fallback) {
        String saved = preferences.get(key, "");
        if (saved.isBlank()) {
            return fallback;
        }
        try {
            return Path.of(saved);
        } catch (InvalidPathException ex) {
            return fallback;
        }
    }

    private void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            preferences.remove(key);
        } else {
            preferences.put(key, value.strip());
        }
    }
}
