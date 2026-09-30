package com.annaschneider.minecraft1.link;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Constants of the Architect Link protocol: newline-delimited UTF-8 JSON messages over a TCP connection to
 * {@code 127.0.0.1}. The desktop app sends {@link LinkRequest}s, the mod answers each with a {@link LinkMessage} of kind
 * {@code response} (same {@code id}) and pushes {@code progress} / {@code log} messages at any time.
 */
public final class LinkProtocol {
    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 47821;
    public static final String LINK_FILE_NAME = "desktop-link.json";
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    /** Largest request line (base64 image plus envelope). */
    public static final int MAX_REQUEST_LINE_BYTES = 12 * 1024 * 1024;
    /** Largest message line sent by the mod. */
    public static final int MAX_MESSAGE_LINE_BYTES = 1024 * 1024;
    public static final int MAX_PROMPT_LENGTH = 500;
    public static final int MAX_SCALE = 24;
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    public static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    public static final Pattern PLAN_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    public static final Pattern TEMPLATE_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    public static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    public static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ -]{0,99}");

    private LinkProtocol() {
    }

    /** Lower-case extension of {@code fileName} without the dot, or an empty string. */
    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Turns a user-chosen name (e.g. a file name or free text) into a valid plan id such as {@code sky-palace}.
     * Returns {@code fallback} when nothing usable remains.
     */
    public static String slug(String text, String fallback) {
        if (text == null) {
            return fallback;
        }
        String ascii = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('\u0111', 'd')
            .replace('\u0110', 'D')
            .toLowerCase(Locale.ROOT);
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 64) {
            slug = slug.substring(0, 64).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? fallback : slug;
    }
}
