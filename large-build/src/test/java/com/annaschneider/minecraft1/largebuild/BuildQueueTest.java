package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.PlacedStructure;
import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionedBlueprint;
import com.annaschneider.minecraft1.largebuild.blueprint.Transform;
import com.annaschneider.minecraft1.largebuild.engine.BuildListener;
import com.annaschneider.minecraft1.largebuild.engine.BuildQueue;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import com.annaschneider.minecraft1.largebuild.generator.IslandGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildQueueTest {
    private static SectionedBlueprint cube(int size, String block) {
        SectionedBlueprint blueprint = new SectionedBlueprint("cube");
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    blueprint.set(x, y, z, block);
                }
            }
        }
        return blueprint;
    }

    private static BuildSettings settings(int blocksPerTick) {
        return BuildSettings.defaults().withBlocksPerTick(blocksPerTick);
    }

    private static int runUntilIdle(BuildQueue queue, MapWorld world, int maxTicks) {
        int ticks = 0;
        while (!queue.snapshot().isEmpty()) {
            queue.tick(world);
            if (++ticks > maxTicks) {
                throw new AssertionError("queue did not finish in " + maxTicks + " ticks");
            }
        }
        return ticks;
    }

    @Test
    void respectsBlocksPerTickAndReportsProgress() {
        BuildQueue queue = new BuildQueue(settings(100));
        MapWorld world = new MapWorld();
        UUID owner = UUID.randomUUID();
        queue.submit(owner, cube(20, "stone"), new Vec3i(0, 0, 0), world);
        BuildQueue.TickReport first = queue.tick(world);
        assertEquals(100, first.blocksChanged());
        assertEquals(100, world.blocks.size());
        JobProgress progress = queue.progress(owner).orElseThrow();
        assertEquals(JobState.RUNNING, progress.state());
        assertTrue(progress.percent() < 100);
        int ticks = runUntilIdle(queue, world, 1_000);
        assertEquals(8_000, world.blocks.size());
        assertTrue(ticks >= 79, "8000 blocks at 100/tick needs at least 80 ticks");
        assertEquals(JobState.COMPLETED, queue.progress(owner).orElseThrow().state());
        assertEquals(100.0, queue.progress(owner).orElseThrow().percent());
    }

    @Test
    void rejectsConflictsFullQueueHeightAndSectionLimits() {
        BuildQueue queue = new BuildQueue(BuildSettings.defaults().withQueueLimits(2, 1));
        MapWorld world = new MapWorld();
        UUID owner = UUID.randomUUID();
        queue.submit(owner, cube(4, "stone"), new Vec3i(0, 0, 0), world);
        IllegalStateException conflict = assertThrows(IllegalStateException.class,
            () -> queue.submit(owner, cube(4, "stone"), new Vec3i(0, 0, 0), world));
        assertTrue(conflict.getMessage().contains("already in progress"));
        queue.submit(UUID.randomUUID(), cube(4, "stone"), new Vec3i(0, 0, 0), world);
        IllegalStateException full = assertThrows(IllegalStateException.class,
            () -> queue.submit(UUID.randomUUID(), cube(4, "stone"), new Vec3i(0, 0, 0), world));
        assertTrue(full.getMessage().contains("queue is full"));

        BuildQueue other = new BuildQueue(BuildSettings.defaults());
        assertThrows(IllegalArgumentException.class,
            () -> other.submit(UUID.randomUUID(), cube(4, "stone"), new Vec3i(0, 318, 0), world));
        assertThrows(IllegalArgumentException.class,
            () -> other.submit(UUID.randomUUID(), cube(4, "stone"), new Vec3i(30_000_000, 0, 0), world));
        BuildQueue tiny = new BuildQueue(new BuildSettings(64, 8, 20, 16, 2, 1, 262_144, null));
        assertThrows(IllegalArgumentException.class,
            () -> tiny.submit(UUID.randomUUID(), cube(20, "stone"), new Vec3i(0, 0, 0), world));
    }

    @Test
    void undoRestoresPreviousBlocksWithJournalSpilledToDisk(@TempDir Path journals) throws Exception {
        BuildSettings settings = BuildSettings.defaults().withBlocksPerTick(4_096).withJournal(1_024, journals);
        BuildQueue queue = new BuildQueue(settings);
        MapWorld world = new MapWorld();
        world.setBlock(3, 3, 3, "minecraft:dirt");
        world.setBlock(40, 0, 0, "minecraft:gold_block");
        Map<Long, String> before = new HashMap<>(world.blocks);
        UUID owner = UUID.randomUUID();
        queue.submit(owner, cube(24, "stone"), new Vec3i(0, 0, 0), world);
        runUntilIdle(queue, world, 100);
        assertEquals("minecraft:stone", world.getBlock(3, 3, 3));
        assertTrue(queue.hasUndo(owner));
        try (var files = Files.list(journals)) {
            assertTrue(files.findAny().isPresent(), "journal should spill to disk above the memory limit");
        }

        JobProgress undo = queue.undoLast(owner);
        assertEquals(24 * 24 * 24, undo.unitsTotal());
        runUntilIdle(queue, world, 100);
        assertEquals(before, world.blocks);
        assertFalse(queue.hasUndo(owner));
        try (var files = Files.list(journals)) {
            assertFalse(files.findAny().isPresent(), "journal files are deleted after undo");
        }
        assertThrows(IllegalStateException.class, () -> queue.undoLast(owner));
    }

    @Test
    void pausedBuildStopsUntilResumedAndCanBeCancelled() {
        BuildQueue queue = new BuildQueue(settings(50));
        MapWorld world = new MapWorld();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(queue.pause(owner, world).isEmpty());
        queue.submit(owner, cube(10, "stone"), new Vec3i(0, 0, 0), world);
        queue.tick(world);
        assertEquals(JobState.PAUSED, queue.pause(owner, world).orElseThrow().state());
        assertEquals(0, queue.tick(world).blocksChanged());
        assertEquals(50, world.blocks.size());
        assertTrue(queue.isBusy(owner));

        queue.submit(other, cube(2, "dirt"), new Vec3i(100, 0, 100), world);
        runUntilIdleExcept(queue, world, owner);
        assertEquals(58, world.blocks.size(), "other jobs keep running while one is paused");

        assertEquals(JobState.RUNNING, queue.resume(owner).orElseThrow().state());
        runUntilIdle(queue, world, 100);
        assertEquals(1_008, world.blocks.size());
        assertEquals(JobState.COMPLETED, queue.progress(owner).orElseThrow().state());

        queue.submit(owner, cube(4, "stone"), new Vec3i(200, 0, 200), world);
        queue.pause(owner, world);
        assertEquals(JobState.CANCELLED, queue.cancel(owner, world).orElseThrow().state());
        assertTrue(queue.resume(owner).isEmpty());
    }

    private static void runUntilIdleExcept(BuildQueue queue, MapWorld world, UUID paused) {
        for (int i = 0; i < 100 && queue.snapshot().size() > 1; i++) {
            queue.tick(world);
        }
        assertEquals(1, queue.snapshot().size());
        assertEquals(paused, queue.snapshot().get(0).owner());
    }

    @Test
    void cancelledBuildCanBeUndone() {
        BuildQueue queue = new BuildQueue(settings(50));
        MapWorld world = new MapWorld();
        UUID owner = UUID.randomUUID();
        queue.submit(owner, cube(10, "stone"), new Vec3i(0, 0, 0), world);
        queue.tick(world);
        queue.tick(world);
        assertEquals(JobState.CANCELLED, queue.cancel(owner, world).orElseThrow().state());
        assertEquals(100, world.blocks.size());
        assertTrue(queue.hasUndo(owner));
        queue.undoLast(owner);
        runUntilIdle(queue, world, 100);
        assertTrue(world.blocks.isEmpty());
    }

    @Test
    void waitsForChunksWithoutBlockingAndReleasesThem() {
        AtomicInteger denials = new AtomicInteger(3);
        Map<Long, Integer> held = new HashMap<>();
        MapWorld world = new MapWorld() {
            @Override
            public boolean prepareChunk(int chunkX, int chunkZ) {
                held.merge(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL), 1, Integer::sum);
                return denials.getAndDecrement() <= 0;
            }

            @Override
            public void releaseChunk(int chunkX, int chunkZ) {
                held.remove(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
            }
        };
        BuildQueue queue = new BuildQueue(settings(10_000));
        UUID owner = UUID.randomUUID();
        queue.submit(owner, cube(20, "stone"), new Vec3i(0, 0, 0), world);
        queue.tick(world);
        assertTrue(queue.progress(owner).orElseThrow().waitingForChunks());
        assertEquals(0, world.blocks.size());
        runUntilIdle(queue, world, 100);
        assertEquals(8_000, world.blocks.size());
        assertTrue(held.isEmpty(), "all chunk holds are released when the job finishes");
    }

    @Test
    void notifiesListenersPerSectionAndSharesBudgetBetweenJobs() {
        BuildQueue queue = new BuildQueue(BuildSettings.defaults().withBlocksPerTick(200).withQueueLimits(4, 2));
        MapWorld world = new MapWorld();
        AtomicInteger sections = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        queue.addListener(new BuildListener() {
            @Override
            public void onSectionCompleted(JobProgress job, Bounds sectionWorldBounds) {
                sections.incrementAndGet();
            }

            @Override
            public void onJobFinished(JobProgress job) {
                finished.incrementAndGet();
            }
        });
        queue.submit(UUID.randomUUID(), cube(16, "stone"), new Vec3i(0, 0, 0), world);
        queue.submit(UUID.randomUUID(), cube(16, "stone"), new Vec3i(64, 0, 0), world);
        BuildQueue.TickReport report = queue.tick(world);
        assertEquals(200, report.blocksChanged());
        runUntilIdle(queue, world, 1_000);
        assertEquals(2, sections.get());
        assertEquals(2, finished.get());
    }

    @Test
    void streamsProceduralBlueprintsWithoutMaterialisingThem() {
        ProceduralBlueprint island = new ProceduralBlueprint("island", List.of(
            new PlacedStructure(new IslandGenerator(30, 20, 5), new Vec3i(0, 0, 0), Transform.IDENTITY)));
        BuildQueue queue = new BuildQueue(settings(16_384));
        MapWorld world = new MapWorld();
        queue.submit(UUID.randomUUID(), island, new Vec3i(0, 64, 0), world);
        runUntilIdle(queue, world, 10_000);
        assertEquals("minecraft:grass_block", world.getBlock(0, 64, 0));
        assertTrue(world.blocks.size() > 10_000);
    }
}
