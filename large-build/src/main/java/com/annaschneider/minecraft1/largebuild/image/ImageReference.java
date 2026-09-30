package com.annaschneider.minecraft1.largebuild.image;

import java.nio.file.Path;

/**
 * A validated image input: either a file inside the uploads directory or a named placeholder (no file needed).
 *
 * @param display human-readable reference ({@code uploads/<file>} or {@code placeholder:<name>})
 * @param name    base name used for keyword hints
 * @param file    resolved file for uploads, {@code null} for placeholders
 * @param format  detected format (png, jpeg, gif, webp) or {@code placeholder}
 * @param sha256  hex digest of the file (or of the placeholder reference)
 */
public record ImageReference(Kind kind, String display, String name, Path file, String format, long sizeBytes,
                             int width, int height, String sha256) {
    public enum Kind {
        UPLOAD,
        PLACEHOLDER
    }
}
