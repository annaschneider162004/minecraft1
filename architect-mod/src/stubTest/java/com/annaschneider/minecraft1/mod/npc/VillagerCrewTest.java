package com.annaschneider.minecraft1.mod.npc;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.engine.JobKind;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import com.annaschneider.minecraft1.largebuild.npc.BuildAgent;
import com.annaschneider.minecraft1.largebuild.npc.BuildCrewCoordinator;
import com.annaschneider.minecraft1.largebuild.npc.NpcSettings;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spawning, bounding, repositioning and removing the visible builder NPCs against the stub Minecraft classes. */
class VillagerCrewTest {
    private static final Bounds AREA = new Bounds(0, 64, 0, 63, 79, 63);

    private MinecraftServer server;
    private ServerWorld world;
    private UUID owner;
    private BuildCrewCoordinator crew;

    @BeforeEach
    void setUp() {
        server = new MinecraftServer();
        world = server.getOverworld();
        owner = UUID.randomUUID();
        server.getPlayerManager().addPlayer(new ServerPlayerEntity(server, world, owner, "Alice"));
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                world.getChunkManager().setChunkLoaded(x, z, true);
            }
        }
        crew = new BuildCrewCoordinator(new VillagerWorkerFactory(server), new NpcSettings(true, 4, 64, 2));
    }

    private JobProgress job(long jobId, JobState state) {
        return new JobProgress(jobId, owner, "palace", JobKind.BUILD, state, 100, 400, 0, 0, 0, false, null);
    }

    private List<Entity> spawned() {
        List<Entity> all = new java.util.ArrayList<>();
        world.iterateEntities().forEach(all::add);
        return all;
    }

    @Test
    void spawnsABoundedTaggedCrewAndAssignsTasks() {
        crew.onJobStarted(job(1, JobState.RUNNING), AREA);
        assertEquals(4, crew.workerCount(), "the crew must stay within maxWorkers");
        assertEquals(4, spawned().size());
        for (Entity entity : spawned()) {
            assertTrue(entity.getCommandTags().contains(VillagerWorkerFactory.WORKER_TAG));
            assertNotNull(entity.getCustomName(), "workers must show their role");
        }
        assertEquals(4, crew.tasks(1).size(), "every worker gets a section slice from AgentWorkPartitioner");
        for (BuildAgent agent : crew.agents(1)) {
            assertFalse(agent.isIdle());
            assertTrue(agent.position().isPresent());
        }
    }

    @Test
    void workersNeverPlaceOrRemoveBlocks() {
        world.setBlockState(new BlockPos(10, 64, 10), Blocks.STONE.getDefaultState(), 3);
        crew.onJobStarted(job(1, JobState.RUNNING), AREA);
        crew.onSectionCompleted(job(1, JobState.RUNNING), new Bounds(16, 64, 16, 31, 79, 31));
        crew.onSectionCompleted(job(1, JobState.RUNNING), new Bounds(32, 64, 32, 47, 79, 47));
        assertEquals(Blocks.STONE, world.getBlockState(new BlockPos(10, 64, 10)).getBlock());
        assertTrue(world.isAir(new BlockPos(20, 70, 20)), "NPCs must not write any block");
    }

    @Test
    void terminalJobStatesRemoveEveryWorker() {
        for (JobState terminal : new JobState[] {JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED}) {
            crew.onJobStarted(job(1, JobState.RUNNING), AREA);
            assertEquals(4, crew.workerCount());
            crew.onJobFinished(job(1, terminal));
            assertEquals(0, crew.workerCount(), "workers must be gone after " + terminal);
            assertTrue(spawned().stream().allMatch(Entity::isRemoved));
        }
    }

    @Test
    void aVanishedJobAndServerShutdownBothClearTheCrew() {
        crew.onJobStarted(job(1, JobState.RUNNING), AREA);
        crew.sync(List.of(job(1, JobState.PAUSED)));
        assertTrue(crew.isPaused(1));
        assertEquals(4, crew.workerCount(), "pausing must not leak or remove workers");
        crew.sync(List.of(job(1, JobState.RUNNING)));
        assertFalse(crew.isPaused(1));
        crew.sync(List.of());
        assertEquals(0, crew.workerCount(), "a job that left the queue takes its crew with it");

        crew.onJobStarted(job(2, JobState.RUNNING), AREA);
        crew.shutdown();
        assertEquals(0, crew.workerCount());
        assertTrue(spawned().stream().allMatch(Entity::isRemoved));
    }

    @Test
    void orphanSweepOnlyRemovesArchitectWorkers() {
        crew.onJobStarted(job(1, JobState.RUNNING), AREA);
        Entity bystander = new Entity(world);
        world.spawnEntity(bystander);
        assertEquals(4, VillagerWorkerFactory.removeOrphans(server));
        assertFalse(bystander.isRemoved());
    }

    @Test
    void jobsOfDifferentWorldsAndOwnersKeepSeparateCrews() {
        ServerWorld nether = new ServerWorld(server, World.NETHER);
        server.addWorld(World.NETHER, nether);
        UUID bob = UUID.randomUUID();
        server.getPlayerManager().addPlayer(new ServerPlayerEntity(server, nether, bob, "Bob"));
        for (int x = -4; x <= 8; x++) {
            for (int z = -4; z <= 8; z++) {
                nether.getChunkManager().setChunkLoaded(x, z, true);
            }
        }
        crew.onJobStarted(job(1, JobState.RUNNING), AREA);
        JobProgress bobsJob = new JobProgress(2, bob, "tower", JobKind.BUILD, JobState.RUNNING, 10, 100, 0, 0, 0, false, null);
        crew.onJobStarted(bobsJob, AREA);
        assertEquals(4, crew.agents(1).size());
        // 100 sections / 64 sections per worker = 2 workers: small jobs get a smaller crew
        assertEquals(2, crew.agents(2).size());
        crew.onJobFinished(bobsJob);
        assertEquals(4, crew.agents(1).size(), "finishing one job must not touch another job's crew");
        assertEquals(0, crew.agents(2).size());
    }
}
