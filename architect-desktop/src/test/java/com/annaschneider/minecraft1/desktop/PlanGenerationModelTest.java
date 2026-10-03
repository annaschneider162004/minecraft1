package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.PlanGenerationOptions;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PlanGenerationModelTest {
    private static PlanGenerationModel.Inputs inputs(String seed, String weights) {
        return new PlanGenerationModel.Inputs("A long cliff city, not a slug", "cliff-city", 2, "Auto", "Auto", seed, weights);
    }

    @Test
    void blankSeedResolvesOnceAndPreviewSaveBuildReuseIt() {
        AtomicLong seeds = new AtomicLong(10);
        PlanGenerationModel model = new PlanGenerationModel(seeds::getAndIncrement);
        PlanGenerationOptions selected = model.resolve(inputs("", ""));
        assertEquals(10L, selected.seed());
        assertSame(selected, model.resolve(inputs("", "")));
        assertSame(selected, model.resolve(inputs("", "")));
        assertEquals(11, seeds.get());
        assertNull(selected.layout());
        assertNull(selected.style());
        assertEquals(11L, model.resolve(new PlanGenerationModel.Inputs(
            "A new full prompt", "cliff-city", 2, "Auto", "Auto", "", "")).seed());
    }

    @Test
    void manualSeedsAcceptSigned64BoundsAndInvalidEditsPreserveSelection() {
        PlanGenerationModel model = new PlanGenerationModel(() -> 9);
        PlanGenerationOptions selected = model.resolve(inputs(Long.toString(Long.MIN_VALUE), ""));
        for (String seed : new String[] {"9223372036854775808", "-9223372036854775809", "1.0", "NaN", "--1"}) {
            assertThrows(IllegalArgumentException.class, () -> model.resolve(inputs(seed, "")));
            assertSame(selected, model.resolve(inputs(Long.toString(Long.MIN_VALUE), "")));
        }
        assertEquals(Long.MAX_VALUE, model.resolve(inputs("+9223372036854775807", "")).seed());
        assertEquals(-17L, model.resolve(inputs(" -17 ", "")).seed());
    }

    @Test
    void variationReplacesAManualSeedAndSurvivesSubsequentActions() {
        PlanGenerationModel model = new PlanGenerationModel(() -> 42);
        assertEquals(42L, model.resolve(inputs("42", "")).seed());
        long newSeed = model.newVariation(inputs("42", ""));
        assertEquals(43, newSeed);
        PlanGenerationOptions variation = model.resolve(inputs(Long.toString(newSeed), ""));
        assertEquals(newSeed, variation.seed());
        assertSame(variation, model.resolve(inputs(Long.toString(newSeed), "")));
        PlanGenerationModel max = new PlanGenerationModel(() -> Long.MAX_VALUE);
        assertEquals(Long.MIN_VALUE, max.newVariation(inputs(Long.toString(Long.MAX_VALUE), "")));
    }

    @Test
    void strictWeightsAndBadVariationDoNotDestroyTheSelectedPlan() {
        assertEquals(Map.of("RADIAL", 1.0, "GRID", 0.0, "LINEAR", 2.5),
            PlanGenerationModel.parseWeights(" radial = 1,GRID=0,Linear=2.5"));
        assertEquals(Map.of(), PlanGenerationModel.parseWeights(""));
        PlanGenerationModel model = new PlanGenerationModel(() -> 12);
        PlanGenerationOptions selected = model.resolve(inputs("", "RADIAL=1"));
        for (String weights : new String[] {
            "RADIAL=-1", "RADIAL=NaN", "RADIAL=Infinity", "RADIAL=1e999", "bogus=1", "RADIAL=1,radial=2",
            "RADIAL", "RADIAL=1,", "=1", "RADIAL=", "RADIAL=0x1p1", "LEGACY=1", "AUTO=1"
        }) {
            assertThrows(IllegalArgumentException.class, () -> model.newVariation(inputs("", weights)), weights);
            assertSame(selected, model.resolve(inputs("", "RADIAL=1")));
        }
        assertThrows(IllegalArgumentException.class, () -> model.newVariation(inputs("bad", "RADIAL=1")));
        assertSame(selected, model.resolve(inputs("", "RADIAL=1")));
    }

    @Test
    void editsToEveryGeometryInputCreateADistinctSelection() {
        PlanGenerationModel model = new PlanGenerationModel(new AtomicLong()::getAndIncrement);
        PlanGenerationModel.Inputs first = inputs("", "");
        PlanGenerationOptions selected = model.resolve(first);
        PlanGenerationOptions layout = model.resolve(new PlanGenerationModel.Inputs(first.prompt(), first.name(),
            first.scale(), "GRID", "medieval", "", "GRID=2"));
        assertNotEquals(selected, layout);
        assertEquals("GRID", layout.layout());
        assertEquals("medieval", layout.style());
        assertEquals(Map.of("GRID", 2.0), layout.weights());
    }
}
