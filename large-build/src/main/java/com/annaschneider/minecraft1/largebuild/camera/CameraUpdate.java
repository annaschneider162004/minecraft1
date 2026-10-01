package com.annaschneider.minecraft1.largebuild.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.engine.JobState;

/**
 * Everything a camera needs to know about one build job. The server produces these snapshots (coalesced, never one per
 * block) and the client feeds them to a {@link CinematicCameraController}; the client never scans the world itself.
 *
 * @param jobId    id of the job; updates with a smaller id than the active one are stale and must be ignored
 * @param worldId  dimension the job builds in, e.g. {@code minecraft:overworld}
 * @param state    job state at the time of the snapshot
 * @param target   full planned bounds of the build, or {@code null} if not known yet
 * @param built    bounds of everything finished so far, or {@code null}
 * @param frontier bounds of the section being built right now, or {@code null}
 */
public record CameraUpdate(
    long jobId,
    String worldId,
    JobState state,
    Bounds target,
    Bounds built,
    Bounds frontier,
    long unitsDone,
    long unitsTotal
) {
    public CameraUpdate {
        if (state == null) {
            throw new IllegalArgumentException("A camera update needs a job state.");
        }
    }

    public double percent() {
        if (state == JobState.COMPLETED) {
            return 100.0;
        }
        return unitsTotal <= 0 ? 0.0 : Math.min(100.0, 100.0 * unitsDone / unitsTotal);
    }

    public boolean isFinished() {
        return state.isFinished();
    }
}
