package com.annaschneider.minecraft1.link;

import java.util.regex.Pattern;

/**
 * Structural validation of {@link LinkRequest}s, shared by the mod (for incoming requests) and the desktop app (before
 * sending). Game-specific checks (unknown template, missing plan, supported blocks, ...) happen in the mod.
 */
public final class RequestValidator {
    private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:/._-]{0,199}");
    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/]*={0,2}");
    private static final java.util.Set<String> CAMERA_MODES =
        java.util.Set.of("auto", "orbit", "follow", "wide", "stop", "status");

    private RequestValidator() {
    }

    /** @throws LinkProtocolException with a user-facing message if the request is invalid */
    public static LinkRequest validate(LinkRequest request) {
        if (request == null) {
            throw new LinkProtocolException("Empty request.");
        }
        if (request.v() != LinkProtocol.VERSION) {
            throw new LinkProtocolException("Unsupported protocol version " + request.v() + "; this build speaks version "
                + LinkProtocol.VERSION + ". Update the desktop app and the mod to the same release.");
        }
        if (request.id() == null || !LinkProtocol.REQUEST_ID.matcher(request.id()).matches()) {
            throw new LinkProtocolException("Request id must be 1-32 characters: letters, digits, '_' or '-'.");
        }
        if (request.type() == null) {
            throw new LinkProtocolException("Unknown or missing request type.");
        }
        switch (request.type()) {
            case HELLO -> {
                require(request.token() != null && !request.token().isBlank() && request.token().length() <= 128,
                    "Missing link token. Reconnect so the app can read desktop-link.json again.");
                require(request.client() == null || request.client().length() <= 64, "Client name is too long.");
                require(request.player() == null || LinkProtocol.PLAYER_NAME.matcher(request.player()).matches(),
                    "Player name must be 1-16 characters: letters, digits or '_'.");
            }
            case UPLOAD_IMAGE -> {
                String name = request.fileName();
                require(name != null && LinkProtocol.FILE_NAME.matcher(name).matches(),
                    "Image file name must be 1-100 characters: letters, digits, '.', '_', '-' or spaces.");
                require(LinkProtocol.IMAGE_EXTENSIONS.contains(LinkProtocol.extension(name)),
                    "Unsupported image type. Use .png, .jpg, .jpeg, .gif or .webp.");
                String data = request.data();
                require(data != null && !data.isEmpty(), "The image is empty.");
                require(data.length() % 4 == 0 && BASE64.matcher(data).matches(), "Image data is not valid base64.");
                long decoded = data.length() / 4L * 3 - (data.endsWith("==") ? 2 : data.endsWith("=") ? 1 : 0);
                require(decoded > 0 && decoded <= LinkProtocol.MAX_IMAGE_BYTES,
                    "The image must be at most " + LinkProtocol.MAX_IMAGE_BYTES / (1024 * 1024) + " MiB.");
            }
            case PLAN -> {
                requirePlanId(request.planId());
                boolean hasSource = request.source() != null && !request.source().isBlank();
                boolean hasPrompt = request.prompt() != null && !request.prompt().isBlank();
                require(hasSource != hasPrompt, "A plan needs either an image source or a prompt (not both).");
                require(!hasSource || SOURCE.matcher(request.source()).matches(),
                    "Image source must look like uploads/<file> or placeholder:<name> (no spaces, at most 200 characters).");
                require(!hasPrompt || request.prompt().length() <= LinkProtocol.MAX_PROMPT_LENGTH,
                    "The prompt must be at most " + LinkProtocol.MAX_PROMPT_LENGTH + " characters.");
                require(request.scale() != null && request.scale() >= 1 && request.scale() <= LinkProtocol.MAX_SCALE,
                    "Scale must be in range 1.." + LinkProtocol.MAX_SCALE + ".");
            }
            case PREVIEW, BUILD -> {
                require(request.mode() != null, "Choose what to " + (request.type() == RequestType.BUILD ? "build" : "preview")
                    + ": mode must be 'template' or 'plan'.");
                if (request.mode() == BuildMode.TEMPLATE) {
                    require(request.template() != null && LinkProtocol.TEMPLATE_ID.matcher(request.template()).matches(),
                        "Choose a template.");
                } else {
                    requirePlanId(request.planId());
                }
            }
            case CAMERA -> {
                require(request.camera() != null || request.npc() != null,
                    "A camera request needs a camera mode or an NPC toggle.");
                require(request.camera() == null || CAMERA_MODES.contains(request.camera().toLowerCase(java.util.Locale.ROOT)),
                    "Camera mode must be auto, orbit, follow, wide, stop or status.");
            }
            case STATUS, PAUSE, RESUME, CANCEL, UNDO, RECORD_START, RECORD_STOP, RECORD_STATUS -> {
                // no parameters
            }
        }
        return request;
    }

    private static void requirePlanId(String planId) {
        require(planId != null && LinkProtocol.PLAN_ID.matcher(planId).matches(),
            "Plan name must be 1-64 characters: a-z, 0-9, '_' or '-', starting with a letter or digit.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new LinkProtocolException(message);
        }
    }
}
