package com.annaschneider.minecraft1.video.voice;

import java.nio.file.Path;
import java.util.Locale;

/** Default local folders of the Video Studio (voices and tools live on disk, never downloaded silently). */
public final class VoiceFolders {
    public static final String ENV_VOICES = "ARCHITECT_VOICES_DIR";

    private VoiceFolders() {
    }

    public static Path defaultVoicesFolder() {
        String override = System.getenv(ENV_VOICES);
        if (override != null && !override.isBlank()) {
            return Path.of(override.strip());
        }
        return appFolder(System.getProperty("os.name", ""), System.getenv("APPDATA"), System.getProperty("user.home", "."))
            .resolve("voices");
    }

    /**
     * Windows: {@code %APPDATA%\MinecraftArchitect}; macOS: {@code ~/Library/Application Support/MinecraftArchitect};
     * Linux: {@code ~/.minecraft-architect}.
     */
    public static Path appFolder(String osName, String appData, String userHome) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return appData != null && !appData.isBlank()
                ? Path.of(appData, "MinecraftArchitect")
                : Path.of(userHome, "AppData", "Roaming", "MinecraftArchitect");
        }
        if (os.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "MinecraftArchitect");
        }
        return Path.of(userHome, ".minecraft-architect");
    }
}
