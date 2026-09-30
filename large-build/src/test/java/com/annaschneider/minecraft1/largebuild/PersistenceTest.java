package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.domain.MirrorAxis;
import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.BlockSection;
import com.annaschneider.minecraft1.largebuild.blueprint.BlueprintSource;
import com.annaschneider.minecraft1.largebuild.blueprint.PlacedStructure;
import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionCursor;
import com.annaschneider.minecraft1.largebuild.blueprint.Transform;
import com.annaschneider.minecraft1.largebuild.engine.BuildQueue;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import com.annaschneider.minecraft1.largebuild.generator.GardenGenerator;
import com.annaschneider.minecraft1.largebuild.generator.IslandGenerator;
import com.annaschneider.minecraft1.largebuild.persistence.BlueprintCodec;
import com.annaschneider.minecraft1.largebuild.persistence.BlueprintStore;
import com.annaschneider.minecraft1.largebuild.persistence.ScenePlanStore;
import com.annaschneider.minecraft1.largebuild.persistence.StoredBlueprint;
import com.annaschneider.minecraft1.largebuild.scene.RegionType;
import com.annaschneider.minecraft1.largebuild.scene.SceneRegion;
import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest {
    private static ProceduralBlueprint sample() {
        return new ProceduralBlueprint("sample", List.of(
            new PlacedStructure(new IslandGenerator(20, 12, 11), new Vec3i(0, 0, 0), Transform.IDENTITY),
            new PlacedStructure(new GardenGenerator(10, 4), new Vec3i(-3, 0, 2), Transform.of(90, MirrorAxis.X))));
    }

    private static Map<String, String> materialise(BlueprintSource source) {
        Map<String, String> cells = new HashMap<>();
        try (SectionCursor cursor = source.openCursor()) {
            BlockSection section = new BlockSection();
            while (cursor.next(section)) {
                for (int i = 0; i < 4096; i++) {
                    String id = source.palette().blockId(section.get(i));
                    if (id != null) {
                        cells.put((section.key().minX() + BlockSection.localX(i)) + "," + (section.key().minY() + BlockSection.localY(i))
                            + "," + (section.key().minZ() + BlockSection.localZ(i)), id);
                    }
                }
            }
        }
        return cells;
    }

    @Test
    void blueprintRoundTripsThroughCompactFormat(@TempDir Path dir) throws Exception {
        ProceduralBlueprint original = sample();
        Path file = dir.resolve("sample.mcab");
        long written = BlueprintCodec.write(original, file, 10_000);
        StoredBlueprint stored = BlueprintCodec.open(file, 10_000);
        assertEquals("sample", stored.name());
        assertEquals(original.bounds(), stored.bounds());
        assertEquals(written, stored.sectionCount());
        Map<String, String> expected = materialise(original);
        assertEquals(expected, materialise(stored));
        assertTrue(Files.size(file) < expected.size(), "RLE + gzip should need well under one byte per block");

        MapWorld world = new MapWorld();
        BuildQueue queue = new BuildQueue(BuildSettings.defaults().withBlocksPerTick(16_384));
        UUID owner = UUID.randomUUID();
        queue.submit(owner, stored, new Vec3i(0, 64, 0), world);
        while (!queue.snapshot().isEmpty()) {
            queue.tick(world);
        }
        assertEquals(JobState.COMPLETED, queue.progress(owner).orElseThrow().state());
        assertEquals(expected.size(), world.blocks.size());
    }

    @Test
    void rejectsCorruptAndOversizedFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.mcab");
        BlueprintCodec.write(sample(), file, 10_000);
        assertThrows(IllegalArgumentException.class, () -> BlueprintCodec.write(sample(), dir.resolve("x.mcab"), 1));
        assertThrows(IllegalArgumentException.class, () -> BlueprintCodec.open(file, 1));

        Path bad = dir.resolve("bad.mcab");
        Files.write(bad, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        IllegalArgumentException magic = assertThrows(IllegalArgumentException.class, () -> BlueprintCodec.open(bad, 10_000));
        assertTrue(magic.getMessage().contains("corrupt"));

        byte[] bytes = Files.readAllBytes(file);
        Path truncated = dir.resolve("truncated.mcab");
        Files.write(truncated, Arrays.copyOf(bytes, bytes.length - 200));
        StoredBlueprint stored = BlueprintCodec.open(truncated, 10_000);
        MapWorld world = new MapWorld();
        BuildQueue queue = new BuildQueue(BuildSettings.defaults().withBlocksPerTick(16_384));
        UUID owner = UUID.randomUUID();
        queue.submit(owner, stored, new Vec3i(0, 64, 0), world);
        while (!queue.snapshot().isEmpty()) {
            queue.tick(world);
        }
        assertEquals(JobState.FAILED, queue.progress(owner).orElseThrow().state());
        assertTrue(queue.hasUndo(owner), "blocks placed before the failure stay undoable");
    }

    @Test
    void blueprintStoreValidatesNames(@TempDir Path dir) {
        BlueprintStore store = new BlueprintStore(dir, 10_000);
        assertThrows(IllegalArgumentException.class, () -> store.pathOf("../escape"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> store.open("nothing"));
        assertTrue(missing.getMessage().contains("No saved blueprint"));
        store.save("island", sample());
        assertEquals(List.of("island"), store.list());
        assertEquals("sample", store.open("island").name());
    }

    @Test
    void scenePlansRoundTripAsJson(@TempDir Path dir) throws Exception {
        ScenePlanStore store = new ScenePlanStore(dir);
        ScenePlan plan = new ScenePlan(ScenePlan.FORMAT_VERSION, "my-plan", "Title", "placeholder:x", "test", "white_gold", 42L, 2,
            List.of(new SceneRegion("island", RegionType.ISLAND, 0, 0, 0, 41, 20, 41, 0, MirrorAxis.NONE, 3L),
                new SceneRegion("bridge", RegionType.BRIDGE, 10, 0, 0, 30, 10, 7, 90, MirrorAxis.Z, 0L)),
            List.of("note"));
        store.save(plan);
        assertEquals(plan, store.load("my-plan"));
        assertEquals(List.of("my-plan"), store.list());

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> store.load("unknown"));
        assertTrue(missing.getMessage().contains("No saved plan 'unknown'"));
        assertThrows(IllegalArgumentException.class, () -> store.load("../etc"));

        Files.writeString(dir.resolve("broken.json"), "{ not json");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.load("broken")).getMessage().contains("invalid"));
        Files.writeString(dir.resolve("badsize.json"), Files.readString(store.pathOf("my-plan"))
            .replace("\"sizeX\": 41", "\"sizeX\": -5").replace("\"id\": \"my-plan\"", "\"id\": \"badsize\""));
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class, () -> store.load("badsize"));
        assertTrue(invalid.getMessage().contains("horizontal size"), invalid.getMessage());
    }
}
