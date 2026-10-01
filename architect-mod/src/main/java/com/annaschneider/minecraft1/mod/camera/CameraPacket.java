package com.annaschneider.minecraft1.mod.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.camera.CameraUpdate;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import com.annaschneider.minecraft1.link.LinkCodec;

/**
 * Wire form of a camera packet: a small, flat JSON object encoded with the same codec as the desktop link. It carries
 * everything the client camera needs - which job is filmed, the dimension, the job state and progress, the full
 * bounds, the area built so far and the section being built - so the client never has to scan world blocks.
 *
 * @param mode     selected camera mode id, or {@code null} for state-only packets
 * @param jobId    job the update belongs to; the client ignores updates of older jobs
 * @param world    dimension id of the job
 * @param state    {@link JobState} name
 * @param target   full planned bounds as {@code [minX, minY, minZ, maxX, maxY, maxZ]}, or {@code null}
 * @param built    bounds completed so far, or {@code null}
 * @param frontier bounds of the section being built, or {@code null}
 */
public record CameraPacket(
    String mode,
    long jobId,
    String world,
    String state,
    int[] target,
    int[] built,
    int[] frontier,
    long unitsDone,
    long unitsTotal
) {
    public static CameraPacket ofMode(CameraMode mode) {
        return new CameraPacket(mode.id(), 0, null, JobState.QUEUED.name(), null, null, null, 0, 0);
    }

    public static CameraPacket ofUpdate(CameraUpdate update, String worldId) {
        return new CameraPacket(null, update.jobId(), worldId, update.state().name(), box(update.target()),
            box(update.built()), box(update.frontier()), update.unitsDone(), update.unitsTotal());
    }

    public String encode() {
        return LinkCodec.encode(this);
    }

    public static CameraPacket decode(String json) {
        return LinkCodec.decode(json, CameraPacket.class);
    }

    /** @return the selected mode, or empty when this packet only carries job state */
    public CameraMode cameraMode() {
        return mode == null || mode.isBlank() ? null : CameraMode.fromId(mode);
    }

    public CameraUpdate toUpdate() {
        JobState jobState;
        try {
            jobState = state == null ? JobState.RUNNING : JobState.valueOf(state);
        } catch (IllegalArgumentException ex) {
            jobState = JobState.RUNNING;
        }
        return new CameraUpdate(jobId, world, jobState, bounds(target), bounds(built), bounds(frontier), unitsDone, unitsTotal);
    }

    private static int[] box(Bounds bounds) {
        return bounds == null ? null
            : new int[] {bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()};
    }

    private static Bounds bounds(int[] values) {
        if (values == null || values.length != 6) {
            return null;
        }
        try {
            return new Bounds(values[0], values[1], values[2], values[3], values[4], values[5]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
