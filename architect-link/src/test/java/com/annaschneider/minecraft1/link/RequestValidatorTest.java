package com.annaschneider.minecraft1.link;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidatorTest {
    private static LinkRequest valid(LinkRequest request) {
        return RequestValidator.validate(request.withId("r1"));
    }

    private static String error(LinkRequest request) {
        return assertThrows(LinkProtocolException.class, () -> valid(request)).getMessage();
    }

    @Test
    void acceptsWellFormedRequests() {
        String png = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        assertDoesNotThrow(() -> valid(LinkRequest.hello("token", "desktop", "Steve")));
        assertDoesNotThrow(() -> valid(LinkRequest.hello("token", null, "")));
        assertDoesNotThrow(() -> valid(LinkRequest.of(RequestType.STATUS)));
        assertDoesNotThrow(() -> valid(LinkRequest.uploadImage("Sky Palace.PNG", png)));
        assertDoesNotThrow(() -> valid(LinkRequest.planFromSource("palace", "uploads/desktop/sky-palace.png", 4)));
        assertDoesNotThrow(() -> valid(LinkRequest.planFromPrompt("castle", "a white castle with waterfalls", 1)));
        assertDoesNotThrow(() -> valid(LinkRequest.preview(BuildMode.TEMPLATE, "house")));
        assertDoesNotThrow(() -> valid(LinkRequest.build(BuildMode.PLAN, "castle")));
        for (RequestType type : new RequestType[] {RequestType.PAUSE, RequestType.RESUME, RequestType.CANCEL, RequestType.UNDO}) {
            assertDoesNotThrow(() -> valid(LinkRequest.of(type)));
        }
    }

    @Test
    void rejectsEnvelopeProblems() {
        assertTrue(assertThrows(LinkProtocolException.class, () -> RequestValidator.validate(LinkRequest.of(RequestType.STATUS)))
            .getMessage().contains("Request id"));
        LinkRequest oldVersion = new LinkRequest(0, "r1", RequestType.STATUS, null, null, null, null, null, null, null, null,
            null, null, null);
        assertTrue(assertThrows(LinkProtocolException.class, () -> RequestValidator.validate(oldVersion))
            .getMessage().contains("Unsupported protocol version 0"));
        assertTrue(error(LinkRequest.of(null)).contains("request type"));
        assertThrows(LinkProtocolException.class, () -> RequestValidator.validate(null));
    }

    @Test
    void rejectsInvalidParameters() {
        assertTrue(error(LinkRequest.hello("", "desktop", null)).contains("link token"));
        assertTrue(error(LinkRequest.hello("t", "desktop", "bad name!")).contains("Player name"));
        assertTrue(error(LinkRequest.uploadImage("../evil.png", "AAAA")).contains("file name"));
        assertTrue(error(LinkRequest.uploadImage("notes.txt", "AAAA")).contains("Unsupported image type"));
        assertTrue(error(LinkRequest.uploadImage("a.png", "not base64!")).contains("base64"));
        assertTrue(error(LinkRequest.uploadImage("a.png", "")).contains("empty"));
        String tooBig = "A".repeat((int) (LinkProtocol.MAX_IMAGE_BYTES / 3 * 4 + 8));
        assertTrue(error(LinkRequest.uploadImage("a.png", tooBig)).contains("at most 8 MiB"));
        assertTrue(error(LinkRequest.planFromPrompt("Bad Id", "castle", 1)).contains("Plan name"));
        assertTrue(error(LinkRequest.planFromSource("ok", "uploads/my file.png", 1)).contains("no spaces"));
        assertTrue(error(LinkRequest.planFromPrompt("ok", "  ", 1)).contains("either an image source or a prompt"));
        assertTrue(error(LinkRequest.planFromPrompt("ok", "castle", 0)).contains("Scale"));
        assertTrue(error(LinkRequest.planFromPrompt("ok", "x".repeat(501), 1)).contains("500"));
        LinkRequest both = new LinkRequest(1, null, RequestType.PLAN, null, null, null, null, null, "ok", "placeholder:a",
            "prompt", 1, null, null);
        assertTrue(error(both).contains("not both"));
        assertTrue(error(LinkRequest.build(null, "house")).contains("mode"));
        assertTrue(error(LinkRequest.build(BuildMode.TEMPLATE, null)).contains("template"));
        assertTrue(error(LinkRequest.preview(BuildMode.PLAN, "../x")).contains("Plan name"));
    }

    @Test
    void slugTurnsFreeTextIntoPlanIds() {
        assertEquals("sky-palace", LinkProtocol.slug("Sky Palace!", "x"));
        assertEquals("cung-dien-thac-nuoc", LinkProtocol.slug("Cung điện, thác nước", "x"));
        assertEquals("x", LinkProtocol.slug("!!!", "x"));
        assertEquals(64, LinkProtocol.slug("a".repeat(100), "x").length());
        assertTrue(LinkProtocol.PLAN_ID.matcher(LinkProtocol.slug("a-".repeat(40), "x")).matches());
    }
}
