package com.annaschneider.minecraft1.largebuild.npc;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.ChunkPartitioner;
import com.annaschneider.minecraft1.largebuild.engine.JobKind;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.engine.JobState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform-neutral scheduler of the visible builder NPCs. One crew per build job: a bounded number of
 * {@link BuildAgent}s created through a {@link WorkerFactory}, given a slice of the job's sections by
 * {@link AgentWorkPartitioner} and repositioned around the section currently being built.
 * <p>
 * The coordinator never writes blocks - the {@link com.annaschneider.minecraft1.largebuild.engine.BuildQueue} stays the
 * only writer - and it removes every worker as soon as a job reaches a terminal state, is gone from the queue or the
 * server shuts down.
 */
public final class BuildCrewCoordinator implements NpcBuildCoordinator {
    /** Creates and removes the platform-specific worker entities. */
    public interface WorkerFactory {
        /**
         * @param near block the worker should appear next to
         * @return the new worker, or empty when it cannot be created right now (chunk not ready, spawning disabled)
         */
        Optional<BuildAgent> create(long jobId, UUID owner, int index, AgentRole role, Vec3i near);

        void dispose(BuildAgent agent);
    }

    /** Roles used, in order, for the workers of a crew. */
    private static final AgentRole[] ROLE_CYCLE = {
        AgentRole.PLANNER, AgentRole.BUILDER, AgentRole.ROAD_WORKER, AgentRole.BUILDER, AgentRole.DECORATOR,
        AgentRole.BUILDER, AgentRole.ROAD_WORKER, AgentRole.DECORATOR, AgentRole.BUILDER, AgentRole.BUILDER,
        AgentRole.DECORATOR, AgentRole.ROAD_WORKER
    };
    /** Upper bound on the section keys loaded to split work; larger jobs are sampled by the partitioner input. */
    private static final int MAX_PARTITIONED_SECTIONS = 65_536;

    private final WorkerFactory factory;
    private final Map<Long, Crew> crews = new LinkedHashMap<>();
    private final Map<Long, Bounds> activeAreas = new LinkedHashMap<>();
    private NpcSettings settings;

    public BuildCrewCoordinator(WorkerFactory factory) {
        this(factory, NpcSettings.defaults());
    }

    public BuildCrewCoordinator(WorkerFactory factory, NpcSettings settings) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public NpcSettings settings() {
        return settings;
    }

    /** Disabling the NPCs removes every worker currently in the world. */
    public void setSettings(NpcSettings value) {
        this.settings = Objects.requireNonNull(value, "settings");
        if (!value.enabled()) {
            shutdown();
        }
    }

    @Override
    public void register(BuildAgent agent) {
        Objects.requireNonNull(agent, "agent");
        crews.computeIfAbsent(0L, Crew::new).agents.add(agent);
    }

    @Override
    public void unregister(BuildAgent agent) {
        for (Crew crew : crews.values()) {
            crew.agents.remove(agent);
        }
    }

    @Override
    public List<BuildAgent> agents() {
        List<BuildAgent> all = new ArrayList<>();
        for (Crew crew : crews.values()) {
            all.addAll(crew.agents);
        }
        return List.copyOf(all);
    }

    public List<BuildAgent> agents(long jobId) {
        Crew crew = crews.get(jobId);
        return crew == null ? List.of() : List.copyOf(crew.agents);
    }

    public int workerCount() {
        return agents().size();
    }

    public boolean isPaused(long jobId) {
        Crew crew = crews.get(jobId);
        return crew != null && crew.paused;
    }

    @Override
    public List<AgentTask> distribute(JobProgress job, long[] sortedSectionKeys) {
        Crew crew = crews.get(job.jobId());
        if (crew == null || crew.agents.isEmpty() || sortedSectionKeys.length == 0) {
            return List.of();
        }
        List<AgentRole> roles = new ArrayList<>(crew.agents.size());
        for (BuildAgent agent : crew.agents) {
            roles.add(agent.role());
        }
        List<AgentTask> tasks = AgentWorkPartitioner.partition(job.jobId(), sortedSectionKeys, roles);
        for (int i = 0; i < crew.agents.size() && i < tasks.size(); i++) {
            crew.agents.get(i).assign(tasks.get(i));
        }
        crew.tasks = tasks;
        return tasks;
    }

    public List<AgentTask> tasks(long jobId) {
        Crew crew = crews.get(jobId);
        return crew == null ? List.of() : List.copyOf(crew.tasks);
    }

    @Override
    public void onJobStarted(JobProgress job, Bounds worldBounds) {
        if (job.kind() == JobKind.BUILD && worldBounds != null) {
            activeAreas.put(job.jobId(), worldBounds);
        }
        if (!settings.enabled() || job.kind() != JobKind.BUILD || worldBounds == null || crews.containsKey(job.jobId())) {
            return;
        }
        Crew crew = new Crew(job.jobId());
        crew.owner = job.owner();
        crew.area = worldBounds;
        int workers = settings.workersFor(job.unitsTotal());
        Vec3i spawn = center(worldBounds);
        for (int i = 0; i < workers; i++) {
            AgentRole role = ROLE_CYCLE[i % ROLE_CYCLE.length];
            factory.create(job.jobId(), job.owner(), i, role, spawn).ifPresent(crew.agents::add);
        }
        if (crew.agents.isEmpty()) {
            return;
        }
        crews.put(job.jobId(), crew);
        long[] keys = ChunkPartitioner.sectionsIntersecting(worldBounds, MAX_PARTITIONED_SECTIONS);
        distribute(job, keys);
        reposition(crew, worldBounds);
    }

    @Override
    public void onSectionCompleted(JobProgress job, Bounds sectionWorldBounds) {
        Crew crew = crews.get(job.jobId());
        if (crew == null || crew.paused || sectionWorldBounds == null) {
            return;
        }
        crew.frontier = sectionWorldBounds;
        if (++crew.sectionsSinceMove >= settings.updateIntervalSections()) {
            crew.sectionsSinceMove = 0;
            reposition(crew, sectionWorldBounds);
        }
    }

    @Override
    public void onJobFinished(JobProgress job) {
        remove(job.jobId());
        activeAreas.remove(job.jobId());
    }

    /**
     * Server-thread safety net driven by the build queue snapshot: pauses crews of paused jobs, resumes them and
     * removes crews whose job disappeared (cancelled, failed, undone or never finished cleanly).
     */
    public void sync(Collection<JobProgress> activeJobs) {
        List<Long> alive = new ArrayList<>(activeJobs.size());
        for (JobProgress job : activeJobs) {
            if (job.kind() != JobKind.BUILD) {
                continue;
            }
            alive.add(job.jobId());
            Crew crew = crews.get(job.jobId());
            if (crew == null && settings.enabled() && activeAreas.containsKey(job.jobId())) {
                onJobStarted(job, activeAreas.get(job.jobId()));
            }
            crew = crews.get(job.jobId());
            if (crew != null) {
                crew.paused = job.state() == JobState.PAUSED;
                if (settings.enabled()) {
                    int wanted = settings.workersFor(job.unitsTotal());
                    boolean changed = false;
                    while (crew.agents.size() > wanted) {
                        factory.dispose(crew.agents.remove(crew.agents.size() - 1));
                        changed = true;
                    }
                    while (crew.agents.size() < wanted) {
                        int index = crew.agents.size();
                        Optional<BuildAgent> added = factory.create(job.jobId(), job.owner(), index,
                            ROLE_CYCLE[index % ROLE_CYCLE.length], center(crew.area));
                        if (added.isEmpty()) {
                            break;
                        }
                        crew.agents.add(added.get());
                        changed = true;
                    }
                    if (changed) {
                        distribute(job, ChunkPartitioner.sectionsIntersecting(crew.area, MAX_PARTITIONED_SECTIONS));
                        reposition(crew, crew.frontier == null ? crew.area : crew.frontier);
                    }
                }
            }
        }
        for (Long jobId : List.copyOf(crews.keySet())) {
            if (!alive.contains(jobId)) {
                remove(jobId);
            }
        }
        activeAreas.keySet().retainAll(alive);
    }

    /** Removes every worker of every job (server stop, world unload, NPCs disabled). */
    public void shutdown() {
        for (Long jobId : List.copyOf(crews.keySet())) {
            remove(jobId);
        }
    }

    private void remove(long jobId) {
        Crew crew = crews.remove(jobId);
        if (crew == null) {
            return;
        }
        for (BuildAgent agent : crew.agents) {
            factory.dispose(agent);
        }
        crew.agents.clear();
    }

    /** Spreads the workers on a ring around the active area and makes them face its centre. */
    private void reposition(Crew crew, Bounds area) {
        Vec3i look = center(area);
        int radius = Math.max(2, Math.min(10, Math.max(area.sizeX(), area.sizeZ()) / 2 + 2));
        int count = crew.agents.size();
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            int x = look.x() + (int) Math.round(Math.cos(angle) * radius);
            int z = look.z() + (int) Math.round(Math.sin(angle) * radius);
            crew.agents.get(i).moveTo(new Vec3i(x, area.maxY() + 1, z), look);
        }
    }

    private static Vec3i center(Bounds area) {
        return new Vec3i((area.minX() + area.maxX()) / 2, (area.minY() + area.maxY()) / 2, (area.minZ() + area.maxZ()) / 2);
    }

    private static final class Crew {
        private final long jobId;
        private final List<BuildAgent> agents = new ArrayList<>();
        private List<AgentTask> tasks = List.of();
        private UUID owner;
        private Bounds area;
        private Bounds frontier;
        private boolean paused;
        private int sectionsSinceMove;

        private Crew(long jobId) {
            this.jobId = jobId;
        }
    }
}
