package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.LinkProtocol;
import com.annaschneider.minecraft1.link.LinkRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** Input checks and conversions done by the desktop app before anything is sent to Minecraft. */
public final class BuildInputs {
    private static final int SUGGESTED_NAME_LENGTH = 32;

    private BuildInputs() {
    }

    /** A short plan name derived from a prompt or file name, e.g. {@code white-castle-with-a-waterfall}. */
    public static String suggestPlanName(String text) {
        String slug = LinkProtocol.slug(text, "my-build");
        if (slug.length() > SUGGESTED_NAME_LENGTH) {
            int cut = slug.lastIndexOf('-', SUGGESTED_NAME_LENGTH);
            slug = slug.substring(0, cut > 0 ? cut : SUGGESTED_NAME_LENGTH);
        }
        return slug;
    }

    public static boolean isValidPlanName(String name) {
        return name != null && LinkProtocol.PLAN_ID.matcher(name).matches();
    }

    /** @return {@code null} if the file can be uploaded, otherwise a user-facing reason */
    public static String checkImage(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return "Choose a picture file first.";
        }
        String extension = LinkProtocol.extension(file.getFileName().toString());
        if (!LinkProtocol.IMAGE_EXTENSIONS.contains(extension)) {
            return "This file type is not supported. Use a .png, .jpg, .jpeg, .gif or .webp picture.";
        }
        try {
            long size = Files.size(file);
            if (size == 0) {
                return "The picture file is empty.";
            }
            if (size > LinkProtocol.MAX_IMAGE_BYTES) {
                return "The picture is too large (" + size / (1024 * 1024) + " MiB). Use one under "
                    + LinkProtocol.MAX_IMAGE_BYTES / (1024 * 1024) + " MiB.";
            }
        } catch (IOException ex) {
            return "Cannot read the picture: " + ex.getMessage();
        }
        return null;
    }

    /** Reads a checked image into an {@code upload_image} request with a safe file name. */
    public static LinkRequest uploadRequest(Path file) throws IOException {
        String problem = checkImage(file);
        if (problem != null) {
            throw new IOException(problem);
        }
        String original = file.getFileName().toString();
        String extension = LinkProtocol.extension(original);
        String base = original.substring(0, original.length() - extension.length() - 1);
        String name = LinkProtocol.slug(base, "image") + "." + extension;
        return LinkRequest.uploadImage(name, Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
    }
}
