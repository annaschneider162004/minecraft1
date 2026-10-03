package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.image.DeterministicScenePlanner;
import com.annaschneider.minecraft1.largebuild.image.HeuristicImageAnalysisProvider;
import com.annaschneider.minecraft1.largebuild.image.ImageAnalysis;
import com.annaschneider.minecraft1.largebuild.image.ImageReference;
import com.annaschneider.minecraft1.largebuild.image.ImageReferenceResolver;
import com.annaschneider.minecraft1.largebuild.image.ImageToBlueprintPipeline;
import com.annaschneider.minecraft1.largebuild.image.PlanOptions;
import com.annaschneider.minecraft1.largebuild.scene.RegionType;
import com.annaschneider.minecraft1.largebuild.scene.SceneCompiler;
import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;
import com.annaschneider.minecraft1.largebuild.scene.ScenePreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePlanningTest {
    static byte[] pngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeInt(13);
        out.writeBytes("IHDR");
        out.writeInt(width);
        out.writeInt(height);
        out.write(new byte[] {8, 6, 0, 0, 0});
        out.writeInt(0);
        return bytes.toByteArray();
    }

    @Test
    void resolvesUploadsSafely(@TempDir Path uploads, @TempDir Path outside) throws Exception {
        Files.write(uploads.resolve("sky-palace.png"), pngHeader(1600, 900));
        Files.writeString(uploads.resolve("fake.png"), "hello");
        Files.write(outside.resolve("secret.png"), pngHeader(10, 10));
        ImageReferenceResolver resolver = new ImageReferenceResolver(uploads);

        ImageReference ref = resolver.resolve("uploads/sky-palace.png");
        assertEquals(ImageReference.Kind.UPLOAD, ref.kind());
        assertEquals("png", ref.format());
        assertEquals(1600, ref.width());
        assertEquals(900, ref.height());
        assertEquals(64, ref.sha256().length());
        assertEquals(ref, resolver.resolve("sky-palace.png"));

        assertTrue(resolver.resolve("placeholder:dream").kind() == ImageReference.Kind.PLACEHOLDER);
        for (String invalid : List.of("../secret.png", "uploads/../secret.png", outside.resolve("secret.png").toString(),
            "a\\b.png", "C:/x.png", "placeholder:../x", "")) {
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(invalid), invalid);
        }
        assertTrue(assertThrows(IllegalArgumentException.class, () -> resolver.resolve("missing.png")).getMessage().contains("not found"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> resolver.resolve("notes.txt")).getMessage().contains("Unsupported"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> resolver.resolve("fake.png")).getMessage().contains("not a recognised"));
    }

    @Test
    void rejectsSymlinkEscapes(@TempDir Path uploads, @TempDir Path outside) throws Exception {
        Files.write(outside.resolve("secret.png"), pngHeader(10, 10));
        try {
            Files.createSymbolicLink(uploads.resolve("link.png"), outside.resolve("secret.png"));
        } catch (UnsupportedOperationException | IOException ex) {
            return;
        }
        ImageReferenceResolver resolver = new ImageReferenceResolver(uploads);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("link.png"));
    }

    @Test
    void planningIsDeterministicAndSeedDependent() {
        DeterministicScenePlanner planner = new DeterministicScenePlanner();
        ImageToBlueprintPipeline pipeline = ImageToBlueprintPipeline.deterministic(new ImageReferenceResolver(Path.of("uploads")));
        ScenePlan a = pipeline.plan("placeholder:dream", new PlanOptions("dream", 2));
        ScenePlan b = pipeline.plan("placeholder:dream", new PlanOptions("dream", 2));
        ScenePlan c = pipeline.plan("placeholder:other", new PlanOptions("dream", 2));
        assertEquals(a, b);
        assertNotEquals(a.seed(), c.seed());
        assertNotEquals(a.regions(), c.regions());
        assertEquals(DeterministicScenePlanner.ID, planner.id());
        Set<RegionType> types = a.regions().stream().map(r -> r.type()).collect(Collectors.toSet());
        assertEquals(EnumSet.of(RegionType.PALACE_CORE, RegionType.BRIDGE, RegionType.TERRACE,
            RegionType.WATERFALL, RegionType.ISLAND, RegionType.PATH, RegionType.GARDEN,
            RegionType.CHERRY_TREES, RegionType.CLOUDS), types, "no keywords -> original nine fantasy features");
        assertEquals(1, a.regions().stream().filter(r -> r.type() == RegionType.PALACE_CORE).count());
        long islands = a.regions().stream().filter(r -> r.type() == RegionType.ISLAND).count();
        assertEquals(islands - 1, a.regions().stream().filter(r -> r.type() == RegionType.BRIDGE).count(),
            "bridges form a spanning tree over the islands");
    }

    @Test
    void keywordsSelectFeatures() {
        HeuristicImageAnalysisProvider provider = new HeuristicImageAnalysisProvider();
        ImageReferenceResolver resolver = new ImageReferenceResolver(Path.of("uploads"));
        ImageAnalysis analysis = provider.analyze(resolver.resolve("placeholder:cherry-garden"));
        assertEquals(EnumSet.of(RegionType.CHERRY_TREES, RegionType.GARDEN, RegionType.ISLAND, RegionType.PALACE_CORE, RegionType.BRIDGE),
            analysis.features());
        ScenePlan plan = new DeterministicScenePlanner().plan(analysis, new PlanOptions("garden", 1));
        assertTrue(plan.regions().stream().noneMatch(r -> r.type() == RegionType.CLOUDS || r.type() == RegionType.WATERFALL));
    }

    @Test
    void scaleGrowsTowardTensOfMillionsOfBlocksWithinSectionLimits() {
        ImageToBlueprintPipeline pipeline = ImageToBlueprintPipeline.deterministic(new ImageReferenceResolver(Path.of("uploads")));
        SceneCompiler compiler = new SceneCompiler();
        long previous = 0;
        for (int scale : new int[] {1, 4, 16}) {
            ScenePlan plan = pipeline.plan("placeholder:kingdom", new PlanOptions("k" + scale, scale));
            ProceduralBlueprint blueprint = compiler.compile(plan, ProceduralBlueprint.DEFAULT_MAX_SECTIONS);
            ScenePreview preview = ScenePreview.of(plan, blueprint);
            assertTrue(preview.estimatedBlocks() > previous);
            previous = preview.estimatedBlocks();
            assertTrue(preview.bounds().minY() >= -63 && preview.bounds().maxY() <= 318 - 64,
                "scene fits the world height when anchored at y=1..64: " + preview.bounds());
            assertTrue(preview.describe().contains("sections"));
        }
        assertTrue(previous > 10_000_000L, "scale 16 should reach tens of millions of blocks, was " + previous);
    }

    @Test
    void validatesPlanOptions() {
        assertThrows(IllegalArgumentException.class, () -> new PlanOptions("Bad Id", 1));
        assertThrows(IllegalArgumentException.class, () -> new PlanOptions("ok", 0));
        assertThrows(IllegalArgumentException.class, () -> new PlanOptions("ok", PlanOptions.MAX_SCALE + 1));
    }
}
