package com.annaschneider.minecraft1.link;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlanGenerationOptionsTest {
    @Test
    void generationRoundTripsAndWithIdPreservesAllChoices() {
        PlanGenerationOptions options = new PlanGenerationOptions("CLIFF", "industrial", Long.MIN_VALUE,
            Map.of("GRID", 0.0, "LINEAR", 2.5));
        String prompt = "A long industrial cliff settlement with a railway and windmills";
        LinkRequest request = LinkRequest.planFromPrompt("settlement", prompt, 3, options).withId("generation");
        assertEquals(options, request.generation());
        assertEquals(prompt, request.prompt());
        assertEquals(request, RequestValidator.validate(LinkCodec.decodeRequest(LinkCodec.encode(request))));
        assertEquals(options, LinkRequest.planFromSource("picture", "placeholder:castle", 1, options).generation());
        LinkProtocolException sourceError = assertThrows(LinkProtocolException.class,
            () -> RequestValidator.validate(
                LinkRequest.planFromSource("picture", "placeholder:castle", 1, options).withId("picture")));
        assertTrue(sourceError.getMessage().contains("options apply to text prompts"));
        LinkRequest friendly = LinkCodec.decodeRequest(
            "{\"v\":1,\"id\":\"friendly\",\"type\":\"plan\",\"planId\":\"settlement\",\"prompt\":\"cliff city\","
                + "\"scale\":1,\"generation\":{\"layout\":\" cliff \",\"style\":\" INDUSTRIAL \","
                + "\"weights\":{\" grid \":2}}}");
        assertEquals(new PlanGenerationOptions("CLIFF", "industrial", null, Map.of("GRID", 2.0)),
            RequestValidator.validate(friendly).generation());
    }

    @Test
    void oldRequestsRetainAbsentGenerationAndOldSettingsConstructor() {
        LinkRequest request = LinkRequest.planFromPrompt("old", "old prompt", 1).withId("old");
        assertNull(request.generation());
        assertFalse(LinkCodec.encode(request).contains("generation"));
        assertEquals(request, LinkCodec.decodeRequest(LinkCodec.encode(request)));
        assertNull(LinkRequest.planFromSource("old", "placeholder:castle", 1).generation());
        LinkRequest settings = LinkRequest.settings(CameraNpcSettings.defaults()).withId("settings");
        LinkRequest copied = new LinkRequest(settings.v(), settings.id(), settings.type(), settings.token(),
            settings.client(), settings.player(), settings.mode(), settings.template(), settings.planId(), settings.source(),
            settings.prompt(), settings.scale(), settings.fileName(), settings.data(), settings.camera(), settings.npc(),
            settings.settings());
        assertEquals(settings, copied);
    }

    @Test
    void validatesNamesWeightsAndCopiesTheMap() {
        for (String layout : PlanGenerationOptions.LAYOUTS) {
            assertEquals(layout, new PlanGenerationOptions(layout, null, Long.MAX_VALUE, null).layout());
        }
        for (String style : PlanGenerationOptions.STYLES) {
            assertEquals(style, new PlanGenerationOptions(null, style, null, null).style());
        }
        assertNull(new PlanGenerationOptions(" auto ", "AUTO", null, null).layout());
        assertNull(new PlanGenerationOptions("AUTO", " Auto ", null, null).style());
        assertEquals(new PlanGenerationOptions("CLIFF", "industrial", 1L, Map.of("GRID", 2.0)),
            new PlanGenerationOptions(" cliff ", " INDUSTRIAL ", 1L, Map.of(" grid ", 2.0)));
        assertThrows(LinkProtocolException.class,
            () -> new PlanGenerationOptions(null, null, null, Map.of("RADIAL", 1.0, " radial ", 2.0)));
        assertThrows(LinkProtocolException.class, () -> new PlanGenerationOptions(null, "unknown", null, null));
        for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(LinkProtocolException.class,
                () -> new PlanGenerationOptions(null, null, null, Map.of("RADIAL", bad)));
        }
        assertThrows(LinkProtocolException.class,
            () -> new PlanGenerationOptions(null, null, null, Map.of("unknown", 1.0)));
        assertThrows(LinkProtocolException.class,
            () -> new PlanGenerationOptions(null, null, null, Map.of("LEGACY", 1.0)));
        Map<String, Double> weights = new HashMap<>(Map.of("RADIAL", 1.0));
        PlanGenerationOptions options = new PlanGenerationOptions(null, null, null, weights);
        weights.put("RADIAL", 9.0);
        assertEquals(1.0, options.weights().get("RADIAL"));
        assertThrows(UnsupportedOperationException.class, () -> options.weights().put("GRID", 1.0));
    }

    @Test
    void malformedGenerationGetsAProtocolErrorRatherThanEscapingTheReader() {
        String prefix = "{\"v\":1,\"id\":\"bad\",\"type\":\"plan\",\"planId\":\"bad\",\"prompt\":\"castle\",\"scale\":1,";
        for (String generation : new String[] {
            "{\"layout\":\"bad\"}", "{\"style\":\"bad\"}", "{\"weights\":{\"RADIAL\":-1}}",
            "{\"weights\":{\"unknown\":1}}", "{\"weights\":{\"RADIAL\":null}}",
            "{\"weights\":{\"RADIAL\":1,\" radial \":2}}", "{\"weights\":{\"LEGACY\":1}}"
        }) {
            assertThrows(LinkProtocolException.class,
                () -> RequestValidator.validate(LinkCodec.decodeRequest(prefix + "\"generation\":" + generation + "}")));
        }
        LinkProtocolException styleError = assertThrows(LinkProtocolException.class,
            () -> LinkCodec.decodeRequest(prefix + "\"generation\":{\"style\":\"secret-user-input\"}}"));
        assertTrue(styleError.getMessage().contains("Unknown style"));
        assertFalse(styleError.getMessage().contains("secret-user-input"));
        assertTrue(styleError.getMessage().length() <= 256);
        LinkProtocolException malformed = assertThrows(LinkProtocolException.class,
            () -> LinkCodec.decodeRequest(prefix + "\"generation\":{\"seed\":\"secret-user-input\"}}"));
        assertEquals("Malformed message: expected one JSON object per line.", malformed.getMessage());
        LinkRequest build = new LinkRequest(1, "bad", RequestType.BUILD, null, null, null, BuildMode.PLAN, null,
            "bad", null, null, null, null, null, null, null, null,
            new PlanGenerationOptions(null, null, 1L, null));
        assertThrows(LinkProtocolException.class, () -> RequestValidator.validate(build));
    }
}
