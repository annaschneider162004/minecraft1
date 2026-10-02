package com.annaschneider.minecraft1.link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodecTest {
    @Test
    void cameraNpcSettingsClampAndRoundTrip() {
        assertEquals(new CameraNpcSettings(false, false, 4, 12, 9), CameraNpcSettings.defaults());
        assertEquals(new CameraNpcSettings(true, true, 12, 5, 30),
            new CameraNpcSettings(true, true, 99, -10, 100));
        assertEquals(new CameraNpcSettings(false, false, 0, 80, 1),
            new CameraNpcSettings(false, false, -2, 200, -5));
        LinkRequest request = LinkRequest.settings(new CameraNpcSettings(true, false, 3, 20, 8)).withId("settings1");
        assertEquals(request, RequestValidator.validate(LinkCodec.decodeRequest(LinkCodec.encode(request))));
        assertThrows(LinkProtocolException.class,
            () -> RequestValidator.validate(LinkRequest.settings(null).withId("settings2")));
    }

    @Test
    void requestsUseLowerCaseIdsAndOmitUnusedFields() {
        String json = LinkCodec.encode(LinkRequest.build(BuildMode.TEMPLATE, "house").withId("7"));
        assertEquals("{\"v\":1,\"id\":\"7\",\"type\":\"build\",\"mode\":\"template\",\"template\":\"house\"}", json);
        LinkRequest decoded = LinkCodec.decodeRequest(json);
        assertEquals(RequestType.BUILD, decoded.type());
        assertEquals(BuildMode.TEMPLATE, decoded.mode());
        assertNull(decoded.planId());
    }

    @Test
    void messagesRoundTrip() {
        LinkMessage message = LinkMessage.ok("3", "Plan ready")
            .withServer(new ServerInfo("2.0.0", 1, "Steve", List.of("house", "castle"), 16, LinkProtocol.MAX_IMAGE_BYTES))
            .withJob(new JobStatus(4, "scene-dream", "build", "paused", 12.5, 10, 80, 1234, false, ""))
            .withPlan(new PlanSummary("dream", "Dream", "summary\nline 2", 10, 20, 30, 999,
                List.of(new RegionBox("palace", -5, -5, 5, 5))));
        String json = LinkCodec.encode(message);
        assertFalse(json.contains("\n"), "one message per line");
        assertTrue(json.contains("\"kind\":\"response\""));
        LinkMessage decoded = LinkCodec.decodeMessage(json);
        assertEquals(message, decoded);
        assertTrue(decoded.job().isPaused());
        assertTrue(decoded.job().isActive());
    }

    @Test
    void unknownTypesAndMalformedJsonAreRejected() {
        assertNull(LinkCodec.decodeRequest("{\"v\":1,\"id\":\"1\",\"type\":\"format_disk\"}").type());
        assertThrows(LinkProtocolException.class, () -> LinkCodec.decodeRequest("GET / HTTP/1.1"));
        assertThrows(LinkProtocolException.class, () -> LinkCodec.decodeRequest(""));
        assertThrows(LinkProtocolException.class, () -> LinkCodec.decodeRequest("[1,2]"));
    }

    @Test
    void readLineIsBounded() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream("one\r\ntwo\nthree".getBytes(StandardCharsets.UTF_8));
        assertEquals("one", LinkCodec.readLine(in, 16));
        assertEquals("two", LinkCodec.readLine(in, 16));
        assertEquals("three", LinkCodec.readLine(in, 16));
        assertNull(LinkCodec.readLine(in, 16));
        ByteArrayInputStream big = new ByteArrayInputStream("x".repeat(100).getBytes(StandardCharsets.UTF_8));
        assertThrows(LinkProtocolException.class, () -> LinkCodec.readLine(big, 16));
    }

    @Test
    void linkInfoRoundTripsAndRejectsIncompleteFiles(@TempDir Path dir) throws IOException {
        LinkInfo info = LinkInfo.create(47821, "2.0.0");
        assertEquals(48, info.token().length());
        assertEquals("127.0.0.1", info.host());
        Path file = dir.resolve("config/architect/" + LinkProtocol.LINK_FILE_NAME);
        info.write(file);
        assertEquals(info, LinkInfo.read(file));
        new LinkInfo(1, "127.0.0.1", 0, "t", "x").write(file);
        assertThrows(IOException.class, () -> LinkInfo.read(file));
        java.nio.file.Files.writeString(file, "not json");
        assertThrows(IOException.class, () -> LinkInfo.read(file));
    }
}
