package com.annaschneider.minecraft1.desktop;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/**
 * Minecraft Architect desktop companion. Usage: {@code architect-desktop [--link-file <path>]}.
 */
public final class ArchitectDesktopApp {
    private ArchitectDesktopApp() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Minecraft Architect needs a desktop session (no display found).");
            System.exit(1);
        }
        DesktopSettings settings = new DesktopSettings();
        for (int i = 0; i < args.length; i++) {
            if ("--link-file".equals(args[i]) && i + 1 < args.length) {
                settings.overrideLinkFile(Path.of(args[++i]));
            } else {
                System.err.println("Unknown argument: " + args[i] + ". Usage: architect-desktop [--link-file <path>]");
            }
        }
        SwingUtilities.invokeLater(() -> {
            try {
                // native look on Windows (and the platform look elsewhere)
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // keep the default cross-platform look
            }
            new MainWindow(settings).start();
        });
    }
}
