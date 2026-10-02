package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.CameraNpcSettings;
import java.nio.file.Path;
import java.util.Objects;
import java.util.prefs.Preferences;

/** Remembered choices (stored per user; the registry on Windows). */
final class DesktopSettings {
    private static final String LINK_FILE = "linkFile";
    private static final String PLAYER = "player";

    private final Preferences preferences;
    private Path overrideLinkFile;

    DesktopSettings() {
        this(Preferences.userNodeForPackage(DesktopSettings.class));
    }

    DesktopSettings(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    CameraNpcSettings cameraNpcSettings() {
        CameraNpcSettings defaults = CameraNpcSettings.defaults();
        return new CameraNpcSettings(preferences.getBoolean("cameraEnabled", defaults.cameraEnabled()),
            preferences.getBoolean("npcEnabled", defaults.npcEnabled()),
            preferences.getInt("maxNpcs", defaults.maxNpcs()),
            preferences.getInt("cameraHeight", defaults.cameraHeight()),
            preferences.getInt("rotationSpeed", defaults.rotationSpeed()));
    }

    void setCameraNpcSettings(CameraNpcSettings value) {
        CameraNpcSettings bounded = new CameraNpcSettings(value.cameraEnabled(), value.npcEnabled(), value.maxNpcs(),
            value.cameraHeight(), value.rotationSpeed());
        preferences.putBoolean("cameraEnabled", bounded.cameraEnabled());
        preferences.putBoolean("npcEnabled", bounded.npcEnabled());
        preferences.putInt("maxNpcs", bounded.maxNpcs());
        preferences.putInt("cameraHeight", bounded.cameraHeight());
        preferences.putInt("rotationSpeed", bounded.rotationSpeed());
    }

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
