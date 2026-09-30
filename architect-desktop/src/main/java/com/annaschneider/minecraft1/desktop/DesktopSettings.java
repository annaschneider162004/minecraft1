package com.annaschneider.minecraft1.desktop;

import java.nio.file.Path;
import java.util.prefs.Preferences;

/** Remembered choices (stored per user; the registry on Windows). */
final class DesktopSettings {
    private static final String LINK_FILE = "linkFile";
    private static final String PLAYER = "player";

    private final Preferences preferences = Preferences.userNodeForPackage(DesktopSettings.class);
    private Path overrideLinkFile;

    /** A {@code --link-file} argument wins over the saved setting for this session. */
    void overrideLinkFile(Path file) {
        overrideLinkFile = file;
    }

    Path linkFile() {
        if (overrideLinkFile != null) {
            return overrideLinkFile;
        }
        String saved = preferences.get(LINK_FILE, "");
        return saved.isBlank() ? LinkFileLocator.defaultLinkFile() : Path.of(saved);
    }

    void setLinkFile(Path file) {
        overrideLinkFile = null;
        if (file == null || file.equals(LinkFileLocator.defaultLinkFile())) {
            preferences.remove(LINK_FILE);
        } else {
            preferences.put(LINK_FILE, file.toString());
        }
    }

    String player() {
        return preferences.get(PLAYER, "");
    }

    void setPlayer(String player) {
        if (player == null || player.isBlank()) {
            preferences.remove(PLAYER);
        } else {
            preferences.put(PLAYER, player.trim());
        }
    }
}
