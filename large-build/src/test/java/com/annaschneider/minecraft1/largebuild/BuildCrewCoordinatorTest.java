package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionKey;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionedBlueprint;
import com.annaschneider.minecraft1.largebuild.camera.BuildCameraDirector;
import com.annaschneider.minecraft1.largebuild.camera.CameraUpdate;
import com.annaschneider.minecraft1.largebuild.camera.DeterministicShotPlanner;
import com.annaschneider.minecraft1.largebuild.engine.BuildQueue;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.largebuild.engine.JobKind;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import com.annaschneider.minecraft1.largebuild.npc.AgentRole;
import com.annaschneider.minecraft1.largebuild.npc.AgentTask;
import com.annaschneider.minecraft1.largebuild.npc.BuildAgent;
import com.annaschneider.minecraft1.largebuild.npc.BuildCrewCoordinator;
import com.annaschneider.minecraft1.largebuild.npc.NpcSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounds, assignment, pause/resume, job isolation and cleanup of the visible builder NPC crews. */
class BuildCrewCoordinatorTest {
    /** Worker stand-in that records what the coordinator asks it to do; it can never touch the world. */
    private static final class FakeAgent implements BuildAgent {
        private final UUID id = UUID.randomUUID();
        private final long jobId;
        private final AgentRole role;
        private AgentTask task;
        private int moves;
        private boolean disposed;

        private FakeAgent(long jobId, AgentRole role) {
            this.jobId = jobId;
            this.role = role;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public String name() {
            return role.displayName();
        }

        @Override
        public AgentRole role() {
            return role;
        }

        @Override
        public Optional<Vec3i> position() {
            return Optional.empty();
        }

        @Override
        public boolean isIdle() {
            return task == null;
        }

        @Override
        public void assign(AgentTask task) {
            this.task = task;
        }

        @Override
        public void moveTo(Vec3i position, Vec3i lookAt) {
            moves++;
        }
    }

    private static final class FakeFactory implements BuildCrewCoordinator.WorkerFactory {
        private final List<FakeAgent> created = new ArrayList<>();

        @Override
        public Optional<BuildAgent> create(long jobId, UUID owner, int index, AgentRole role, Vec3i near) {
            FakeAgent agent = new FakeAgent(jobId, role);
            created.add(agent);
            return Optional.of(agent);
        }

        @Override
        public void dispose(BuildAgent agent) {
            ((FakeAgent) agent).disposed = true;
        }

        private long alive() {
            return created.stream().filter(agent -> !agent.disposed).count();
        }
    }

    private static JobProgress job(long id, UUID owner, JobState state, long sections) {
        return new JobProgress(id, owner, "job" + id, JobKind.BUILD, state, 0, sections, 0, 0, 0, false, "");
    }

    private static SectionedBlueprint cube(int size) {
        SectionedBlueprint blueprint = new SectionedBlueprint("cube");
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    blueprint.set(x, y, z, "minecraft:stone");
                }
            }
        }
        return blueprint;
    }

    @Test
    void workerCountStaysBoundedAndEverySectionIsAssignedOnce() {
        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, new NpcSettings(true, 4, 64, 2));
        UUID owner = UUID.randomUUID();
        crew.onJobStarted(job(1, owner, JobState.RUNNING, 100_000), new Bounds(0, 0, 0, 255, 127, 255));

        assertEquals(4, crew.agents(1).size(), "never more workers than configured, whatever the job size");
        List<AgentTask> tasks = crew.tasks(1);
        assertEquals(4, tasks.size());
        Set<Long> sections = new HashSet<>();
        for (AgentTask task : tasks) {
            for (long key : task.sectionKeys()) {
                assertTrue(sections.add(key), "a section is assigned to one worker only");
            }
        }
        assertFalse(sections.isEmpty());
        for (BuildAgent agent : crew.agents(1)) {
            assertFalse(agent.isIdle(), "every worker has a slice of the build");
        }
    }

    @Test
    void resizingLiveCrewAppliesNewMaximum() {
        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, new NpcSettings(true, 4, 16, 2));
        UUID owner = UUID.randomUUID();
        var running = job(1, owner, JobState.RUNNING, 256);
        crew.onJobStarted(running, new Bounds(0, 0, 0, 31, 31, 31));
        assertEquals(4, factory.alive());
        crew.setSettings(new NpcSettings(true, 1, 16, 2));
        crew.sync(List.of(running));
        assertEquals(1, factory.alive());
        crew.setSettings(new NpcSettings(true, 3, 16, 2));
        crew.sync(List.of(running));
        assertEquals(3, factory.alive());
        crew.shutdown();
        assertEquals(0, factory.alive());
    }

    @Test
    void enablingDuringAnActiveBuildCreatesWorkers() {
        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, new NpcSettings(false, 2, 16, 2));
        var running = job(1, UUID.randomUUID(), JobState.RUNNING, 64);
        crew.onJobStarted(running, new Bounds(0, 0, 0, 31, 31, 31));
        assertEquals(0, factory.alive());
        crew.setSettings(new NpcSettings(true, 2, 16, 2));
        crew.sync(List.of(running));
        assertEquals(2, factory.alive());
        crew.setSettings(new NpcSettings(false, 2, 16, 2));
        assertEquals(0, factory.alive());
    }

    @Test
    void smallJobsGetASingleWorkerAndDisabledNpcsGetNone() {
        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, NpcSettings.defaults());
        crew.onJobStarted(job(1, UUID.randomUUID(), JobState.RUNNING, 4), new Bounds(0, 0, 0, 15, 15, 15));
        assertEquals(1, crew.agents(1).size());

        crew.setSettings(NpcSettings.defaults().withEnabled(false));
        assertEquals(0, factory.alive(), "disabling the NPCs removes the workers already in the world");
        crew.onJobStarted(job(2, UUID.randomUUID(), JobState.RUNNING, 4), new Bounds(0, 0, 0, 15, 15, 15));
        assertEquals(0, crew.agents(2).size());
    }

    @Test
    void workersFollowTheFrontierAndFreezeWhilePaused() {
        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, new NpcSettings(true, 3, 16, 2));
        UUID owner = UUID.randomUUID();
        JobProgress running = job(1, owner, JobState.RUNNING, 64);
        crew.onJobStarted(running, new Bounds(0, 0, 0, 63, 63, 63));
        int movesAfterSpawn = ((FakeAgent) crew.agents(1).get(0)).moves;

        crew.onSectionCompleted(running, new SectionKey(1, 0, 1).bounds());
        crew.onSectionCompleted(running, new SectionKey(2, 0, 1).bounds());
        assertTrue(((FakeAgent) crew.agents(1).get(0)).moves > movesAfterSpawn, "workers follow the build frontier");

        crew.sync(List.of(job(1, owner, JobState.PAUSED, 64)));
        assertTrue(crew.isPaused(1));
        int movesWhilePaused = ((FakeAgent) crew.agents(1).get(0)).moves;
        crew.onSectionCompleted(running, new SectionKey(3, 0, 1).bounds());
        crew.onSectionCompleted(running, new SectionKey(4, 0, 1).bounds());
        assertEquals(movesWhilePaused, ((FakeAgent) crew.agents(1).get(0)).moves, "a paused job does not reassign workers");
        assertEquals(3, factory.alive(), "pausing must not leak or remove workers");

        crew.sync(List.of(running));
        assertFalse(crew.isPaused(1));
        crew.onSectionCompleted(running, new SectionKey(5, 0, 1).bounds());
        crew.onSectionCompleted(running, new SectionKey(6, 0, 1).bounds());
        assertTrue(((FakeAgent) crew.agents(1).get(0)).moves > movesWhilePaused, "resuming continues cleanly");
    }

    @Test
    void everyTerminalStateAndShutdownRemovesAllWorkers() {
        for (JobState terminal : new JobState[] {JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED}) {
            FakeFactory factory = new FakeFactory();
            BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, NpcSettings.defaults());
            UUID owner = UUID.randomUUID();
            crew.onJobStarted(job(1, owner, JobState.RUNNING, 200), new Bounds(0, 0, 0, 63, 63, 63));
            assertTrue(factory.alive() > 0);
            crew.onJobFinished(job(1, owner, terminal, 200));
            assertEquals(0, factory.alive(), terminal + " must remove every worker");
            assertTrue(crew.agents().isEmpty());
        }

        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, NpcSettings.defaults());
        crew.onJobStarted(job(1, UUID.randomUUID(), JobState.RUNNING, 200), new Bounds(0, 0, 0, 63, 63, 63));
        crew.shutdown();
        assertEquals(0, factory.alive(), "server shutdown removes every worker");

        // a job that simply vanishes from the queue (undo, failure before finishing) is cleaned up by the sync pass
        crew.onJobStarted(job(2, UUID.randomUUID(), JobState.RUNNING, 200), new Bounds(0, 0, 0, 63, 63, 63));
        assertTrue(factory.alive() > 0);
        crew.sync(List.of());
        assertEquals(0, factory.alive());
    }

    @Test
    void jobsOfDifferentPlayersKeepSeparateCrews() {
        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, new NpcSettings(true, 2, 16, 2));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        crew.onJobStarted(job(1, alice, JobState.RUNNING, 32), new Bounds(0, 0, 0, 63, 63, 63));
        crew.onJobStarted(job(2, bob, JobState.RUNNING, 32), new Bounds(500, 0, 500, 563, 63, 563));
        assertEquals(2, crew.agents(1).size());
        assertEquals(2, crew.agents(2).size());

        crew.onJobFinished(job(1, alice, JobState.COMPLETED, 32));
        assertTrue(crew.agents(1).isEmpty());
        assertEquals(2, crew.agents(2).size(), "the other player's crew is untouched");
        assertEquals(2, factory.alive());
    }

    @Test
    void npcsNeverPlaceBlocksAndTheQueueStaysTheOnlyWriter() {
        MapWorld world = new MapWorld();
        BuildQueue queue = new BuildQueue(BuildSettings.defaults());
        FakeFactory factory = new FakeFactory();
        BuildCrewCoordinator crew = new BuildCrewCoordinator(factory, NpcSettings.defaults());
        BuildCameraDirector camera = new BuildCameraDirector(new DeterministicShotPlanner());
        queue.addListener(crew);
        queue.addListener(camera);
        UUID owner = UUID.randomUUID();

        queue.submit(owner, cube(16), new Vec3i(0, 0, 0), world);
        int ticks = 0;
        while (!queue.snapshot().isEmpty()) {
            queue.tick(world);
            crew.sync(queue.snapshot());
            if (++ticks > 10_000) {
                throw new AssertionError("build did not finish");
            }
        }

        assertEquals(16 * 16 * 16, world.blocks.size());
        assertEquals(16 * 16 * 16, world.writes, "workers are cosmetic: no duplicate placements");
        assertEquals(0, factory.alive(), "the crew is gone once the job completed");
        CameraUpdate update = camera.snapshot(owner).orElseThrow();
        assertSame(JobState.COMPLETED, update.state());
        assertEquals(100.0, update.percent());
    }
}
