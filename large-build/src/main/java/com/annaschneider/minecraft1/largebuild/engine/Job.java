package com.annaschneider.minecraft1.largebuild.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

abstract class Job {
    final long id;
    final UUID owner;
    final String name;
    final JobKind kind;
    JobState state = JobState.QUEUED;
    long unitsDone;
    long blocksChanged;
    long blocksUnchanged;
    long ticks;
    boolean waitingForChunks;
    String message = "";
    JobState stateBeforePause;
    private final List<Long> heldChunks = new ArrayList<>(4);

    Job(long id, UUID owner, String name, JobKind kind) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.kind = kind;
    }

    /** Performs bounded work. Returns {@code true} once the job is complete. */
    abstract boolean step(WorldAccess world, TickBudget budget, BuildQueue queue);

    abstract long unitsTotal();

    /** Releases open resources (cursors, readers, chunk tickets). */
    void close(WorldAccess world) {
        releaseAll(world);
    }

    JobProgress progress() {
        return new JobProgress(id, owner, name, kind, state, unitsDone, unitsTotal(), blocksChanged, blocksUnchanged,
            ticks, waitingForChunks, message);
    }

    /**
     * Ensures the chunk columns spanning [x0,x1] x [z0,z1] are ready; releases other held columns. Returns false when
     * the world asked us to wait.
     */
    boolean holdChunks(WorldAccess world, int x0, int z0, int x1, int z1) {
        List<Long> wanted = new ArrayList<>(4);
        for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x1, 16); cx++) {
            for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z1, 16); cz++) {
                wanted.add(pack(cx, cz));
            }
        }
        boolean ready = true;
        for (long key : wanted) {
            if (!world.prepareChunk(unpackX(key), unpackZ(key))) {
                ready = false;
            }
        }
        for (long key : heldChunks) {
            if (!wanted.contains(key)) {
                world.releaseChunk(unpackX(key), unpackZ(key));
            }
        }
        heldChunks.clear();
        heldChunks.addAll(wanted);
        waitingForChunks = !ready;
        return ready;
    }

    void releaseAll(WorldAccess world) {
        if (world != null) {
            for (long key : heldChunks) {
                world.releaseChunk(unpackX(key), unpackZ(key));
            }
        }
        heldChunks.clear();
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
