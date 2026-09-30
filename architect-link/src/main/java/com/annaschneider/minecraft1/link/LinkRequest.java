package com.annaschneider.minecraft1.link;

/**
 * A request from the desktop app. One flat JSON object per line; fields that do not apply to {@link #type()} are
 * {@code null} and omitted on the wire. Use the factory methods to create well-formed requests and
 * {@link RequestValidator} to check incoming ones.
 *
 * @param v        protocol version, always {@link LinkProtocol#VERSION}
 * @param id       client-chosen correlation id echoed in the response
 * @param token    link token from {@code desktop-link.json} ({@code hello} only)
 * @param client   free-form client name ({@code hello} only)
 * @param player   Minecraft player to act for ({@code hello} only; empty = the first player online)
 * @param mode     {@code template} or {@code plan} ({@code preview} / {@code build})
 * @param template template id ({@code mode=template})
 * @param planId   plan id ({@code plan}, and {@code preview}/{@code build} with {@code mode=plan})
 * @param source   {@code uploads/<file>} or {@code placeholder:<name>} ({@code plan})
 * @param prompt   free text describing the build ({@code plan}, instead of {@code source})
 * @param scale    1..24, size of generated scenes ({@code plan})
 * @param fileName image file name ({@code upload_image})
 * @param data     base64-encoded image bytes ({@code upload_image})
 */
public record LinkRequest(
    int v,
    String id,
    RequestType type,
    String token,
    String client,
    String player,
    BuildMode mode,
    String template,
    String planId,
    String source,
    String prompt,
    Integer scale,
    String fileName,
    String data
) {
    public static LinkRequest of(RequestType type) {
        return new LinkRequest(LinkProtocol.VERSION, null, type, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static LinkRequest hello(String token, String client, String player) {
        return new LinkRequest(LinkProtocol.VERSION, null, RequestType.HELLO, token, client, blankToNull(player), null, null,
            null, null, null, null, null, null);
    }

    public static LinkRequest uploadImage(String fileName, String base64Data) {
        return new LinkRequest(LinkProtocol.VERSION, null, RequestType.UPLOAD_IMAGE, null, null, null, null, null, null,
            null, null, null, fileName, base64Data);
    }

    public static LinkRequest planFromSource(String planId, String source, int scale) {
        return new LinkRequest(LinkProtocol.VERSION, null, RequestType.PLAN, null, null, null, null, null, planId, source,
            null, scale, null, null);
    }

    public static LinkRequest planFromPrompt(String planId, String prompt, int scale) {
        return new LinkRequest(LinkProtocol.VERSION, null, RequestType.PLAN, null, null, null, null, null, planId, null,
            prompt, scale, null, null);
    }

    public static LinkRequest preview(BuildMode mode, String templateOrPlanId) {
        return target(RequestType.PREVIEW, mode, templateOrPlanId);
    }

    public static LinkRequest build(BuildMode mode, String templateOrPlanId) {
        return target(RequestType.BUILD, mode, templateOrPlanId);
    }

    private static LinkRequest target(RequestType type, BuildMode mode, String value) {
        return new LinkRequest(LinkProtocol.VERSION, null, type, null, null, null, mode,
            mode == BuildMode.TEMPLATE ? value : null, mode == BuildMode.PLAN ? value : null, null, null, null, null, null);
    }

    public LinkRequest withId(String newId) {
        return new LinkRequest(v, newId, type, token, client, player, mode, template, planId, source, prompt, scale, fileName, data);
    }

    /** Short description for logs; never includes the token or image data. */
    public String describe() {
        StringBuilder text = new StringBuilder(type == null ? "unknown" : type.name().toLowerCase(java.util.Locale.ROOT));
        if (mode != null) {
            text.append(' ').append(mode.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (template != null) {
            text.append(' ').append(template);
        }
        if (planId != null) {
            text.append(' ').append(planId);
        }
        if (fileName != null) {
            text.append(' ').append(fileName);
        }
        return text.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
