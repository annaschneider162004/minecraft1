package com.annaschneider.minecraft1.largebuild.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.engine.BuildListener;
import com.annaschneider.minecraft1.largebuild.engine.JobKind;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks what each owner is building so a camera can frame it: the full target area once a build starts and the
 * area actually completed so far. {@link #suggest} turns either into a shot via the configured planner.
 */
public final class BuildCameraDirector implements BuildListener {
    private final CameraShotPlanner planner;
    private final Map<UUID, Bounds> targets = new HashMap<>();
    private final Map<UUID, Bounds> built = new HashMap<>();

    public BuildCameraDirector(CameraShotPlanner planner) {
        this.planner = planner;
    }

    @Override
    public void onJobStarted(JobProgress job, Bounds worldBounds) {
        if (job.kind() == JobKind.BUILD && worldBounds != null) {
            targets.put(job.owner(), worldBounds);
            built.remove(job.owner());
        }
    }

    @Override
    public void onSectionCompleted(JobProgress job, Bounds sectionWorldBounds) {
        built.merge(job.owner(), sectionWorldBounds, Bounds::union);
    }

    public Optional<Bounds> target(UUID owner) {
        return Optional.ofNullable(targets.get(owner));
    }

    public Optional<Bounds> builtArea(UUID owner) {
        return Optional.ofNullable(built.get(owner));
    }

    /** Shot around the completed area if any, otherwise around the planned target. */
    public Optional<CameraPath> suggest(UUID owner, ShotType type, double durationSeconds) {
        Bounds area = built.getOrDefault(owner, targets.get(owner));
        return area == null ? Optional.empty() : Optional.of(planner.plan(type, area, durationSeconds));
    }
}
