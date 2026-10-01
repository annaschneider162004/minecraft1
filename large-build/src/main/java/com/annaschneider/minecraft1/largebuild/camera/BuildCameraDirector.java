package com.annaschneider.minecraft1.largebuild.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.engine.BuildListener;
import com.annaschneider.minecraft1.largebuild.engine.JobKind;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.engine.JobState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks what each owner is building so a camera can frame it: the full target area once a build starts, the area
 * actually completed so far and the section being built right now. {@link #suggest} turns any of them into a planned
 * shot; {@link #pollUpdate} produces the coalesced snapshots that drive the live cinematic camera.
 * <p>
 * Called on the server thread by {@link com.annaschneider.minecraft1.largebuild.engine.BuildQueue}.
 */
public final class BuildCameraDirector implements BuildListener {
    private final CameraShotPlanner planner;
    private final Map<UUID, Bounds> targets = new HashMap<>();
    private final Map<UUID, Bounds> built = new HashMap<>();
    private final Map<UUID, Tracked> tracked = new HashMap<>();

    public BuildCameraDirector(CameraShotPlanner planner) {
        this.planner = planner;
    }

    @Override
    public void onJobStarted(JobProgress job, Bounds worldBounds) {
        if (job.kind() == JobKind.BUILD && worldBounds != null) {
            targets.put(job.owner(), worldBounds);
            built.remove(job.owner());
            Tracked state = new Tracked(job.jobId());
            state.target = worldBounds;
            state.apply(job);
            tracked.put(job.owner(), state);
        }
    }

    @Override
    public void onSectionCompleted(JobProgress job, Bounds sectionWorldBounds) {
        built.merge(job.owner(), sectionWorldBounds, Bounds::union);
        Tracked state = current(job);
        if (state != null) {
            state.built = state.built == null ? sectionWorldBounds : state.built.union(sectionWorldBounds);
            state.frontier = sectionWorldBounds;
            state.dirty = true;
            state.apply(job);
        }
    }

    @Override
    public void onJobFinished(JobProgress job) {
        Tracked state = current(job);
        if (state != null) {
            state.apply(job);
        }
    }

    /** Refreshes the tracked state of a job without geometry changes (pause, resume, progress ticks). */
    public void refresh(JobProgress job) {
        Tracked state = current(job);
        if (state != null) {
            state.apply(job);
        }
    }

    public Optional<Bounds> target(UUID owner) {
        return Optional.ofNullable(targets.get(owner));
    }

    public Optional<Bounds> builtArea(UUID owner) {
        return Optional.ofNullable(built.get(owner));
    }

    /** Current snapshot of the job tracked for {@code owner}, whether it changed or not. */
    public Optional<CameraUpdate> snapshot(UUID owner) {
        Tracked state = tracked.get(owner);
        return state == null ? Optional.empty() : Optional.of(state.toUpdate());
    }

    /**
     * Returns a snapshot only when something changed since the last poll, so the server sends bounded, coalesced
     * updates instead of one packet per placed block.
     */
    public Optional<CameraUpdate> pollUpdate(UUID owner) {
        Tracked state = tracked.get(owner);
        if (state == null || !state.dirty) {
            return Optional.empty();
        }
        state.dirty = false;
        return Optional.of(state.toUpdate());
    }

    /** Drops everything remembered for an owner (player left, world unloaded, server stopped). */
    public void forget(UUID owner) {
        tracked.remove(owner);
        targets.remove(owner);
        built.remove(owner);
    }

    public void forgetAll() {
        tracked.clear();
        targets.clear();
        built.clear();
    }

    /** Shot around the completed area if any, otherwise around the planned target. */
    public Optional<CameraPath> suggest(UUID owner, ShotType type, double durationSeconds) {
        Bounds area = built.getOrDefault(owner, targets.get(owner));
        return area == null ? Optional.empty() : Optional.of(planner.plan(type, area, durationSeconds));
    }

    /** Events of an older job of the same owner are ignored. */
    private Tracked current(JobProgress job) {
        Tracked state = tracked.get(job.owner());
        return state != null && state.jobId == job.jobId() ? state : null;
    }

    private static final class Tracked {
        private final long jobId;
        private Bounds target;
        private Bounds built;
        private Bounds frontier;
        private JobState state = JobState.QUEUED;
        private long unitsDone;
        private long unitsTotal;
        private boolean dirty = true;

        private Tracked(long jobId) {
            this.jobId = jobId;
        }

        private void apply(JobProgress job) {
            if (state != job.state() || unitsDone != job.unitsDone() || unitsTotal != job.unitsTotal()) {
                dirty = true;
            }
            state = job.state();
            unitsDone = job.unitsDone();
            unitsTotal = job.unitsTotal();
        }

        private CameraUpdate toUpdate() {
            return new CameraUpdate(jobId, null, state, target, built, frontier, unitsDone, unitsTotal);
        }
    }
}
