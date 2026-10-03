package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.domain.MirrorAxis;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.PlacedStructure;
import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.image.*;
import com.annaschneider.minecraft1.largebuild.persistence.ScenePlanStore;
import com.annaschneider.minecraft1.largebuild.scene.*;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiLayoutPlanningTest {
    private final MultiLayoutScenePlanner planner = new MultiLayoutScenePlanner();
    private final SceneCompiler compiler = new SceneCompiler();

    private static PlanOptions options(LayoutType layout, int scale, long seed) {
        return new PlanOptions("settlement", scale, layout, null, seed, Map.of());
    }
    private ScenePlan plan(LayoutType layout, int scale, long seed) {
        return planner.planText("neutral village", options(layout, scale, seed));
    }

    @Test void distinctStructuralArchetypesAndStableRegistry() {
        assertEquals(LayoutGeneratorRegistry.ORDER, new LayoutGeneratorRegistry().generators().stream().map(LayoutGenerator::layout).toList());
        Set<List<SceneRegion>> geometries = new HashSet<>();
        for (LayoutType layout : LayoutGeneratorRegistry.ORDER) {
            ScenePlan plan = plan(layout, 2, 44);
            assertEquals(layout, plan.metadata().layout());
            assertTrue(plan.regions().stream().anyMatch(r -> r.type() == RegionType.BUILDING));
            assertTrue(plan.regions().stream().anyMatch(r -> r.type() == RegionType.ROAD));
            assertFalse(plan.regions().stream().anyMatch(r -> r.type() == RegionType.ISLAND || r.type() == RegionType.PALACE_CORE));
            assertTrue(geometries.add(plan.regions()));
        }
        Bounds line = compiler.compile(plan(LayoutType.LINEAR, 2, 44), 100_000).bounds();
        assertTrue(line.sizeX() > line.sizeZ() * 3);
        assertTrue(plan(LayoutType.CLIFF, 2, 44).regions().stream().anyMatch(r -> r.type() == RegionType.ROCK));
        assertTrue(plan(LayoutType.CLIFF, 2, 44).regions().stream().anyMatch(r -> r.type() == RegionType.LEDGE));
        Bounds cliff = compiler.compile(plan(LayoutType.CLIFF, 2, 44), 100_000).bounds();
        assertTrue(cliff.sizeY() > cliff.sizeX() * 2, "cliff must have a vertical facade, not sloped terraces");
        assertEquals(1, plan(LayoutType.CLIFF, 2, 44).regions().stream().filter(r -> r.type() == RegionType.BUILDING)
            .map(SceneRegion::x).distinct().count());
        assertTrue(plan(LayoutType.CLIFF, 2, 44).regions().stream().anyMatch(r -> r.type() == RegionType.STAIR && r.rotation() != 0));
        assertTrue(plan(LayoutType.TERRACED, 2, 44).regions().stream().anyMatch(r -> r.type() == RegionType.STAIR));
        assertFalse(plan(LayoutType.RING, 2, 44).regions().stream().anyMatch(r ->
            r.type() != RegionType.FOUNDATION && r.x() <= 0 && r.x() + r.sizeX() > 0
                && r.z() <= 0 && r.z() + r.sizeZ() > 0));
    }

    @Test void geometryAndJsonAreDeterministicButVaryBySeed() {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        for (LayoutType layout : LayoutGeneratorRegistry.ORDER) {
            ScenePlan first = plan(layout, 2, 567);
            assertEquals(first, plan(layout, 2, 567));
            assertEquals(gson.toJson(first), gson.toJson(plan(layout, 2, 567)));
            assertNotEquals(first.regions(), plan(layout, 2, 568).regions());
        }
        assertEquals(planner.planText("medieval city", options(LayoutType.AUTO, 2, 123)),
            planner.planText("medieval city", options(LayoutType.AUTO, 2, 123)));
    }

    @Test void weightedBatchTracksConfiguredProportionsAndFiltersBeforeSampling() {
        EnumMap<LayoutType, Integer> counts = new EnumMap<>(LayoutType.class);
        for (long seed = 0; seed < 2000; seed++) {
            ScenePlan p = planner.planText("village", new PlanOptions("sample", 1, LayoutType.AUTO, "neutral", seed,
                Map.of(LayoutType.LINEAR, 1.0, LayoutType.GRID, 3.0)));
            counts.merge(p.metadata().layout(), 1, Integer::sum);
        }
        assertEquals(Set.of(LayoutType.LINEAR, LayoutType.GRID), counts.keySet());
        double fraction = counts.get(LayoutType.GRID) / 2000.0;
        assertTrue(fraction > .70 && fraction < .80, counts.toString());
        for (long seed = 0; seed < 30; seed++) {
            ScenePlan p = planner.planText("mountain village", new PlanOptions("filtered", 1, LayoutType.AUTO, "neutral", seed,
                Map.of(LayoutType.GRID, 1e10, LayoutType.TERRACED, 1.0)));
            assertEquals(LayoutType.TERRACED, p.metadata().layout());
            assertFalse(p.metadata().weights().containsKey(LayoutType.GRID));
        }
        ScenePlan huge = planner.planText("village", new PlanOptions("huge", 1, LayoutType.AUTO, null, 4L,
            Map.of(LayoutType.GRID, Double.MAX_VALUE, LayoutType.LINEAR, Double.MAX_VALUE)));
        assertNotNull(huge.metadata());
    }

    @Test void validatesWeightsStylesAndIncompatibleConstraints() {
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> new PlanOptions("x", 1, LayoutType.AUTO, null, 1L,
                Map.of(LayoutType.GRID, invalid))).getMessage().contains("finite non-negative"));
        }
        assertThrows(IllegalArgumentException.class, () -> new PlanOptions("x", 1, null, null, null, Map.of(LayoutType.AUTO, 1.0)));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> planner.planText("mountain village",
            new PlanOptions("x", 1, LayoutType.AUTO, null, 1L, Map.of(LayoutType.GRID, 1.0))))
            .getMessage().contains("after compatible filtering"));
        assertThrows(IllegalArgumentException.class, () -> planner.planText("village",
            new PlanOptions("x", 1, LayoutType.AUTO, null, 1L, Map.of(LayoutType.GRID, 0.0))));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> planner.planText("village",
            new PlanOptions("x", 1, LayoutType.GRID, "unicorn", 1L, Map.of()))).getMessage().contains("Unknown style"));
        assertThrows(IllegalArgumentException.class, () -> planner.planText("style:unicorn village", options(LayoutType.AUTO, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> planner.planText("cliff village", options(LayoutType.GRID, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> planner.planText("no grid", options(LayoutType.GRID, 1, 1)));
        ScenePlan unknown = planner.planText("village without unicorns", options(LayoutType.AUTO, 1, 1));
        assertTrue(unknown.notes().stream().anyMatch(n -> n.contains("Unknown exclusion")));
        assertTrue(planner.planText("village without towers and dragons", options(LayoutType.AUTO, 1, 1))
            .notes().stream().anyMatch(n -> n.contains("dragons")));
        assertEquals(LayoutType.CLIFF, planner.planText("industrial cliff village",
            options(LayoutType.CLIFF, 1, 1)).metadata().layout());
    }

    @Test void precedenceAndWholePromptParsing() {
        ScenePlan explicit = planner.planText("medieval linear village", new PlanOptions("x", 1, LayoutType.GRID,
            "desert", 1L, Map.of()));
        assertEquals(LayoutType.GRID, explicit.metadata().layout());
        assertEquals("desert", explicit.style());
        assertEquals("option", explicit.metadata().parameters().get("selection"));
        ScenePlan styleOverride = planner.planText("industrial village with towers", new PlanOptions("x", 1, LayoutType.GRID,
            "medieval", 1L, Map.of()));
        assertTrue(styleOverride.regions().stream().anyMatch(r -> r.type() == RegionType.TOWER));
        assertFalse(styleOverride.notes().stream().anyMatch(n -> n.contains("omitted")));
        ScenePlan prompt = planner.planText("a village with gardens and towers. medieval ring layout, without floating islands and central palace",
            options(LayoutType.AUTO, 1, 1));
        assertEquals(LayoutType.RING, prompt.metadata().layout());
        assertEquals("medieval", prompt.style());
        assertEquals("village", prompt.metadata().subject());
        assertTrue(prompt.metadata().features().containsAll(List.of("garden", "tower")));
        assertTrue(prompt.metadata().exclusions().containsAll(List.of("island", "palace")));
        assertTrue(prompt.regions().stream().anyMatch(r -> r.type() == RegionType.TOWER));
        assertTrue(prompt.regions().stream().anyMatch(r -> r.type() == RegionType.PLANTING));
        assertThrows(IllegalArgumentException.class, () -> planner.planText("a".repeat(4097), options(LayoutType.AUTO, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> planner.planText("layout=diagonal", options(LayoutType.AUTO, 1, 1)));
        ScenePlan contrast = planner.planText("no towers but gardens with trees, medieval village",
            options(LayoutType.GRID, 1, 1));
        assertFalse(contrast.metadata().features().contains("tower"));
        assertTrue(contrast.metadata().features().containsAll(List.of("garden", "trees")));
    }

    @Test void presetsChangeMaterialsAndSupportedStructures() {
        Set<Set<String>> palettes = new HashSet<>();
        for (StylePreset style : StylePreset.values()) {
            ScenePlan p = planner.planText("village with towers and trees", new PlanOptions("x", 1, LayoutType.GRID,
                style.id(), 22L, Map.of()));
            Set<String> materials = new HashSet<>();
            compiler.compile(p, 100_000).structures().forEach(s -> materials.addAll(s.generator().palette()));
            assertTrue(materials.contains(style.masonry()));
            assertTrue(palettes.add(materials));
            if (style == StylePreset.INDUSTRIAL) {
                assertFalse(p.regions().stream().anyMatch(r -> r.type() == RegionType.TOWER || r.type() == RegionType.GROVE));
                assertTrue(p.notes().stream().anyMatch(n -> n.contains("Unsupported feature")));
            }
        }
    }

    @Test void exclusionsPreventForbiddenGeometryAndNeutralFallbackIsGrounded() {
        for (LayoutType layout : LayoutGeneratorRegistry.ORDER) {
            ScenePlan p = planner.planText("fantasy village with towers gardens trees water, without towers gardens trees water floating islands central palace",
                options(layout, 1, 9));
            assertFalse(p.regions().stream().anyMatch(r -> Set.of(RegionType.TOWER, RegionType.PLANTING, RegionType.GROVE,
                RegionType.POOL, RegionType.ISLAND, RegionType.PALACE_CORE).contains(r.type())));
            assertFalse(compiler.compile(p, 100_000).structures().stream().anyMatch(s -> s.generator().palette().contains("minecraft:water")));
        }
        ScenePlan blank = planner.planText(null, options(LayoutType.AUTO, 1, 42));
        assertEquals("neutral", blank.style());
        assertEquals("settlement", blank.metadata().subject());
        assertNotNull(blank.metadata());
        assertFalse(blank.regions().stream().anyMatch(r -> r.type() == RegionType.ISLAND || r.type() == RegionType.PALACE_CORE));
        ScenePlan islandRequest = planner.planText("island", options(LayoutType.AUTO, 1, 42));
        assertTrue(islandRequest.notes().stream().anyMatch(n -> n.contains("Unsupported feature 'island'")));
        String fullPrompt = "  medieval village with a waterfall, without floating islands and central palace  ";
        ScenePlan sourced = planner.planText(fullPrompt, options(LayoutType.LINEAR, 1, -42));
        assertEquals("prompt:" + fullPrompt, sourced.source());
        assertEquals(fullPrompt, sourced.metadata().prompt());
        assertTrue(sourced.notes().stream().anyMatch(n -> n.contains("Selected layout: LINEAR")));
        assertTrue(sourced.regions().stream().anyMatch(r -> r.type() == RegionType.WATERFALL));
        assertFalse(sourced.notes().stream().anyMatch(n -> n.contains("Unsupported feature 'waterfall'")));
        ScenePlan waterExcluded = planner.planText("medieval village with waterfall, no water", options(LayoutType.LINEAR, 1, 1));
        assertFalse(waterExcluded.regions().stream().anyMatch(r -> r.type() == RegionType.WATERFALL || r.type() == RegionType.POOL));
    }

    @Test void imageSeedFallbackExplicitSeedAndLegacyArePreserved() {
        ImageReference image = new ImageReferenceResolver(Path.of("uploads")).resolve("placeholder:dream");
        ImageAnalysis analysis = new HeuristicImageAnalysisProvider().analyze(image);
        assertEquals(analysis.seed(), planner.plan(analysis, new PlanOptions("x", 1)).seed());
        assertEquals(12, planner.plan(analysis, new PlanOptions("x", 1, LayoutType.AUTO, null, 12L, Map.of())).seed());
        ScenePlan legacy = new DeterministicScenePlanner().plan(analysis, new PlanOptions("x", 1));
        assertEquals(legacy, planner.plan(analysis, new PlanOptions("x", 1, LayoutType.LEGACY, null, null, Map.of())));
        assertNull(legacy.metadata());
        assertEquals(LayoutType.LEGACY, planner.planText("legacy palace", options(LayoutType.AUTO, 1, 1)).metadata().layout());
        assertThrows(IllegalArgumentException.class, () -> planner.planText("legacy palace without floating islands",
            options(LayoutType.AUTO, 1, 1)));
        assertFalse(planner.planText("legacy palace", options(LayoutType.AUTO, 1, 1)).notes().stream()
            .anyMatch(n -> n.contains("Unsupported feature 'palace'")));
    }

    @Test void unknownNaturalStylesTerrainAndConstraintsAreHonestlyDiagnosed() {
        for (String hint : List.of("steampunk", "gothic", "sci-fi", "volcanic basalt")) {
            ScenePlan p = planner.planText(hint + " village", options(LayoutType.AUTO, 1, 7));
            assertEquals("neutral", p.style());
            assertTrue(p.notes().stream().anyMatch(n -> n.contains("Unknown style/material hint")), hint);
            assertTrue(p.notes().stream().anyMatch(n -> n.contains("Uninterpreted prompt vocabulary")), hint);
        }
        ScenePlan p = planner.planText("medieval symmetrical village facing sunrise with rotating gears on volcanic terrain",
            options(LayoutType.AUTO, 1, 7));
        assertTrue(p.notes().stream().anyMatch(n -> n.contains("Unsupported terrain hint 'volcanic'")));
        for (String constraint : List.of("symmetrical", "facing sunrise", "rotating gears")) {
            assertTrue(p.notes().stream().anyMatch(n -> n.contains("Unsupported constraint '" + constraint + "'")));
        }
        assertEquals("flat", p.metadata().terrain());
    }

    @Test void waterfallsAndDecorativeFeaturesAreSupportedSeparateAndConnected() {
        for (LayoutType layout : LayoutGeneratorRegistry.ORDER) {
            ScenePlan p = planner.planText("medieval village with towers gardens trees water walls waterfall",
                options(layout, 2, 4));
            ProceduralBlueprint blueprint = compiler.compile(p, 100_000);
            Set<Cell> walk = walkable(p);
            assertEquals(walk.size(), reachable(walk).size(), layout.toString());
            if (layout == LayoutType.CLIFF) for (Cell cell : walk) {
                assertTrue(solid(blockAt(blueprint, cell.x(), cell.y(), cell.z())));
                assertFalse(solid(blockAt(blueprint, cell.x(), cell.y() + 1, cell.z())));
                assertFalse(solid(blockAt(blueprint, cell.x(), cell.y() + 2, cell.z())));
            }
            SceneRegion waterfall = p.regions().stream().filter(r -> r.type() == RegionType.WATERFALL).findFirst().orElseThrow();
            assertEquals("minecraft:water", blockAt(blueprint, waterfall.x(), waterfall.y(), waterfall.z()));
            assertTrue(solid(blockAt(blueprint, waterfall.x(), waterfall.y(), waterfall.z() - 1)));
            assertTrue(solid(blockAt(blueprint, waterfall.x(), waterfall.y() - waterfall.sizeY(), waterfall.z() - 1)));
            for (SceneRegion r : p.regions()) if (r.type() == RegionType.GROVE) {
                assertTrue(solid(blockAt(blueprint, r.x() + 3, r.y() - 1, r.z() + 3)));
            }
        }
    }

    @Test void metadataRoundtripResolvedFreshSeedAndOldFixtureGeometry() throws Exception {
        Path dir = Path.of("build", "core-plan-test").toAbsolutePath();
        Files.createDirectories(dir);
        ScenePlanStore store = new ScenePlanStore(dir);
        try {
            ScenePlan fresh = planner.planText("medieval town with gardens", new PlanOptions("settlement", 1));
            store.save(fresh);
            assertEquals(fresh, store.load("settlement"));
            assertEquals(fresh, planner.planText(fresh.metadata().prompt(),
                new PlanOptions(fresh.id(), fresh.scale(), LayoutType.AUTO, null, fresh.seed(), Map.of())));
            try (var fixture = getClass().getResourceAsStream("/scene-v1.json")) {
                assertNotNull(fixture);
                Files.copy(fixture, store.pathOf("old_fixture"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            ScenePlan old = store.load("old_fixture");
            assertNull(old.metadata());
            ScenePlan expected = new ScenePlan(1, "old_fixture", "Original v1 fixture", "placeholder:legacy",
                "deterministic-kingdom-v1", "white_gold", 17, 1, List.of(
                new SceneRegion("island", RegionType.ISLAND, 0, 0, 0, 33, 8, 33, 0, MirrorAxis.NONE, 17),
                new SceneRegion("path", RegionType.PATH, -8, 1, 0, 17, 1, 3, 0, MirrorAxis.NONE, 17)),
                List.of("Unmodified v1 geometry"));
            assertEquals(expected, old);
            ProceduralBlueprint a = compiler.compile(old, 1000), b = compiler.compile(expected, 1000);
            assertEquals(a.bounds(), b.bounds());
            var fingerprint = java.security.MessageDigest.getInstance("SHA-256");
            for (int x = -16; x <= 16; x++) for (int y = -8; y <= 1; y++) for (int z = -16; z <= 16; z++) {
                assertEquals(blockAt(a, x, y, z), blockAt(b, x, y, z));
                fingerprint.update((x + "," + y + "," + z + "=" + blockAt(a, x, y, z) + "\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals("06942bd11d1004e643d6fc6120434d729aa06d69988d0741ff25d4f964488163",
                HexFormat.of().formatHex(fingerprint.digest()));
            store.save(old);
            assertNull(store.load("old_fixture").metadata());
        } finally {
            Files.deleteIfExists(store.pathOf("settlement"));
            Files.deleteIfExists(store.pathOf("old_fixture"));
            Files.deleteIfExists(dir);
        }
    }

    @Test void smallAndMaximumScalesHaveComputedBoundsSupportConnectivityAndBudget() {
        for (LayoutType layout : LayoutGeneratorRegistry.ORDER) for (int scale : new int[]{1, 24}) {
            ScenePlan p = plan(layout, scale, 7);
            ProceduralBlueprint blueprint = compiler.compile(p, 100_000);
            Bounds union = null;
            for (PlacedStructure s : blueprint.structures()) union = union == null ? s.bounds() : union.union(s.bounds());
            assertEquals(union, blueprint.bounds());
            assertTrue(blueprint.bounds().minY() >= -3 && blueprint.bounds().maxY() < 255);
            assertTrue(blueprint.sectionCount() < 100_000);
            assertThrows(IllegalArgumentException.class, () -> compiler.compile(p, 1));
            Set<Cell> walk = walkable(p);
            Set<Cell> visited = reachable(walk);
            assertEquals(walk.size(), visited.size(), layout + " scale " + scale + " has disconnected roads");
            if (layout == LayoutType.CLIFF) for (Cell cell : walk) {
                assertTrue(solid(blockAt(blueprint, cell.x(), cell.y(), cell.z())), "cliff walkway must exist: " + cell);
                assertFalse(solid(blockAt(blueprint, cell.x(), cell.y() + 1, cell.z())), "cliff access needs headroom: " + cell);
                assertFalse(solid(blockAt(blueprint, cell.x(), cell.y() + 2, cell.z())), "cliff access needs two-block headroom: " + cell);
            }
            for (SceneRegion r : p.regions()) if (r.type() == RegionType.BUILDING || r.type() == RegionType.TOWER) {
                for (int dx : new int[]{0, r.sizeX() - 1}) for (int dz : new int[]{0, r.sizeZ() - 1}) {
                    assertTrue(solid(blockAt(blueprint, r.x() + dx, r.y() - 1, r.z() + dz)), layout + " unsupported building " + r.id());
                }
                Cell door = new Cell(r.x() + r.sizeX() / 2, r.y(), r.z() - 1);
                assertTrue(visited.contains(door), layout + " inaccessible door " + r.id() + " at " + door);
            }
        }
    }

    private record Cell(int x, int y, int z) {}
    private static Set<Cell> walkable(ScenePlan p) {
        Set<Cell> cells = new HashSet<>();
        for (SceneRegion r : p.regions()) {
            if (r.type() != RegionType.ROAD && r.type() != RegionType.STAIR) continue;
            for (int x = 0; x < r.sizeX(); x++) for (int z = 0; z < r.sizeZ(); z++) {
                var transform = com.annaschneider.minecraft1.largebuild.blueprint.Transform.of(r.rotation(), r.mirror());
                cells.add(new Cell(r.x() + transform.applyX(x, z),
                    r.y() + (r.type() == RegionType.STAIR ? Math.min(r.sizeY() - 1, x) : 0),
                    r.z() + transform.applyZ(x, z)));
            }
        }
        return cells;
    }
    private static Set<Cell> reachable(Set<Cell> walk) {
        Set<Cell> visited = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Cell start = walk.iterator().next();
        visited.add(start); queue.add(start);
        while (!queue.isEmpty()) {
            Cell c = queue.removeFirst();
            for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for (int dy = -1; dy <= 1; dy++) {
                Cell next = new Cell(c.x + direction[0], c.y + dy, c.z + direction[1]);
                if (walk.contains(next) && visited.add(next)) queue.addLast(next);
            }
        }
        return visited;
    }
    private static boolean solid(String block) { return block != null && !block.equals("minecraft:air") && !block.equals("minecraft:water"); }
    private static String blockAt(ProceduralBlueprint b, int x, int y, int z) {
        String result = null;
        for (PlacedStructure s : b.structures()) {
            if (!s.bounds().contains(x, y, z)) continue;
            String block = s.blockAt(x, y, z);
            if (block != null) result = block;
        }
        return result;
    }
}
