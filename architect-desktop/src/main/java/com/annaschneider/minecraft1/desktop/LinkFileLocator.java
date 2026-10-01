package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.LinkProtocol;

import java.nio.file.Path;
import java.util.Locale;

/** Where the mod writes {@code desktop-link.json} for the default Minecraft launcher. */
public final class LinkFileLocator {
    private LinkFileLocator() {
    }

    public static Path defaultLinkFile() {
        return defaultLinkFile(System.getProperty("os.name", ""), System.getenv("APPDATA"), System.getProperty("user.home", "."));
    }

    /**
     * Windows: {@code %APPDATA%\.minecraft\config\architect\desktop-link.json}; macOS:
     * {@code ~/Library/Application Support/minecraft/...}; Linux: {@code ~/.minecraft/...}.
     */
    static Path defaultLinkFile(String osName, String appData, String userHome) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path gameDir;
        if (os.contains("win")) {
            gameDir = appData != null && !appData.isBlank()
                ? Path.of(appData, ".minecraft")
                : Path.of(userHome, "AppData", "Roaming", ".minecraft");
        } else if (os.contains("mac")) {
            gameDir = Path.of(userHome, "Library", "Application Support", "minecraft");
        } else {
            gameDir = Path.of(userHome, ".minecraft");
        }
        return gameDir.resolve("config").resolve("architect").resolve(LinkProtocol.LINK_FILE_NAME);
    }
}
