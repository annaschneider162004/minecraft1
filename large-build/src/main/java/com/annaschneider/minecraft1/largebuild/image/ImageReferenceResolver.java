package com.annaschneider.minecraft1.largebuild.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates image references. Uploaded files must live inside the configured uploads directory (no absolute paths,
 * no {@code ..}, no symlink escapes), use a supported extension, match an image signature and stay under the size cap.
 */
public final class ImageReferenceResolver {
    public static final String PLACEHOLDER_PREFIX = "placeholder:";
    public static final String UPLOADS_PREFIX = "uploads/";
    public static final long DEFAULT_MAX_BYTES = 32L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ -]{0,127}");
    private static final int HEADER_BYTES = 64 * 1024;

    private final Path uploadsRoot;
    private final long maxBytes;

    public ImageReferenceResolver(Path uploadsDirectory) {
        this(uploadsDirectory, DEFAULT_MAX_BYTES);
    }

    public ImageReferenceResolver(Path uploadsDirectory, long maxBytes) {
        this.uploadsRoot = uploadsDirectory.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public Path uploadsRoot() {
        return uploadsRoot;
    }

    public ImageReference resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Image reference is empty. Use uploads/<file>.png or placeholder:<name>.");
        }
        String ref = reference.trim();
        if (ref.regionMatches(true, 0, PLACEHOLDER_PREFIX, 0, PLACEHOLDER_PREFIX.length())) {
            return placeholder(ref.substring(PLACEHOLDER_PREFIX.length()).toLowerCase(Locale.ROOT));
        }
        return upload(ref.startsWith(UPLOADS_PREFIX) ? ref.substring(UPLOADS_PREFIX.length()) : ref);
    }

    private ImageReference placeholder(String name) {
        if (!PLACEHOLDER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid placeholder name '" + name + "'. Use 1-64 characters: a-z, 0-9, '_' or '-'.");
        }
        String display = PLACEHOLDER_PREFIX + name;
        return new ImageReference(ImageReference.Kind.PLACEHOLDER, display, name, null, "placeholder", 0, 0, 0,
            sha256(display.getBytes(StandardCharsets.UTF_8)));
    }

    private ImageReference upload(String relative) {
        String display = UPLOADS_PREFIX + relative;
        if (relative.isEmpty() || relative.contains("\\") || relative.startsWith("/") || relative.contains(":")) {
            throw new IllegalArgumentException("Invalid image path '" + display + "'. Use a relative path such as uploads/my-palace.png.");
        }
        for (String segment : relative.split("/", -1)) {
            if (!SAFE_SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid image path '" + display + "'. Path segments may only use letters, digits, '.', '_', '-' or spaces.");
            }
        }
        Path candidate;
        try {
            candidate = uploadsRoot.resolve(relative).normalize();
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid image path '" + display + "'.");
        }
        if (!candidate.startsWith(uploadsRoot)) {
            throw new IllegalArgumentException("Invalid image path '" + display + "': it must stay inside the uploads directory.");
        }
        String fileName = candidate.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported image type for '" + display + "'. Use .png, .jpg, .jpeg, .gif or .webp.");
        }
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Image not found: " + display + ". Copy the file into " + uploadsRoot + " first.");
        }
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(uploadsRoot.toRealPath())) {
                throw new IllegalArgumentException("Invalid image path '" + display + "': it must stay inside the uploads directory.");
            }
            long size = Files.size(real);
            if (size <= 0 || size > maxBytes) {
                throw new IllegalArgumentException("Image '" + display + "' must be between 1 byte and " + (maxBytes / (1024 * 1024)) + " MiB.");
            }
            return readMetadata(display, fileName.substring(0, dot), real, size);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read image '" + display + "': " + ex.getMessage());
        }
    }

    private ImageReference readMetadata(String display, String baseName, Path file, long size) throws IOException {
        MessageDigest digest = newDigest();
        byte[] header = new byte[HEADER_BYTES];
        int headerLength = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
                if (headerLength < HEADER_BYTES) {
                    int copy = Math.min(read, HEADER_BYTES - headerLength);
                    System.arraycopy(buffer, 0, header, headerLength, copy);
                    headerLength += copy;
                }
            }
        }
        ImageHeaders.Info info = ImageHeaders.parse(header, headerLength);
        if (info == null) {
            throw new IllegalArgumentException("File '" + display + "' is not a recognised PNG, JPEG, GIF or WebP image.");
        }
        return new ImageReference(ImageReference.Kind.UPLOAD, display, baseName.toLowerCase(Locale.ROOT), file,
            info.format(), size, info.width(), info.height(), HexFormat.of().formatHex(digest.digest()));
    }

    static String sha256(byte[] data) {
        return HexFormat.of().formatHex(newDigest().digest(data));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
