package com.annaschneider.minecraft1.largebuild.engine;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.BlueprintSource;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bounded FIFO build queue driven by the server tick. At most one job per owner, at most
 * {@link BuildSettings#maxQueuedJobs()} jobs overall, and the per-tick block/section/time budget is shared by the first
 * {@link BuildSettings#maxConcurrentJobs()} jobs. Not thread-safe: call from the server thread only.
 */
public final class BuildQueue {
    /** Horizontal limit that keeps builds inside the vanilla world border. */
    public static final int MAX_HORIZONTAL = 29_999_000;

    private final BuildSettings settings;
    private final LinkedHashMap<UUID, Job> jobs = new LinkedHashMap<>();
    private final Map<UUID, UndoJournal> history = new HashMap<>();
    private final Map<UUID, String> historyNames = new HashMap<>();
    private final Map<UUID, JobProgress> lastFinished = new HashMap<>();
    private final List<BuildListener> listeners = new CopyOnWriteArrayList<>();
    private long nextId = 1;

    public BuildQueue(BuildSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public BuildSettings settings() {
        return settings;
    }

    public void addListener(BuildListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public JobProgress submit(UUID owner, BlueprintSource source, Vec3i origin, WorldAccess world) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(origin, "origin");
        requireAvailable(owner);
        if (source.sectionCount() > settings.maxSectionsPerJob()) {
            throw new IllegalArgumentException("Blueprint has " + source.sectionCount() + " sections; the limit is "
                + settings.maxSectionsPerJob() + ".");
        }
        Bounds target = source.bounds().translated(origin.x(), origin.y(), origin.z());
        if (target.minY() < world.minY() || target.maxY() > world.maxY()) {
            throw new IllegalArgumentException("Build spans y=" + target.minY() + ".." + target.maxY()
                + " but the world allows y=" + world.minY() + ".." + world.maxY() + ". Move the origin up or down.");
        }
        if (Math.abs((long) target.minX()) > MAX_HORIZONTAL || Math.abs((long) target.maxX()) > MAX_HORIZONTAL
            || Math.abs((long) target.minZ()) > MAX_HORIZONTAL || Math.abs((long) target.maxZ()) > MAX_HORIZONTAL) {
            throw new IllegalArgumentException("Build would extend beyond the world border.");
        }
        UndoJournal journal = new UndoJournal(settings.journalDirectory(), settings.journalMemoryEntries());
        BuildJob job = new BuildJob(nextId++, owner, source, origin, journal);
        jobs.put(owner, job);
        return job.progress();
    }

    /** Queues an undo of the owner's latest finished build. */
    public JobProgress undoLast(UUID owner) {
        requireAvailable(owner);
        UndoJournal journal = history.remove(owner);
        String name = historyNames.remove(owner);
        if (journal == null) {
            throw new IllegalStateException("No completed build to undo.");
        }
        UndoJob job = new UndoJob(nextId++, owner, name, journal);
        jobs.put(owner, job);
        return job.progress();
    }

    public boolean hasUndo(UUID owner) {
        return history.containsKey(owner);
    }

    /** Cancels the owner's build; blocks already placed stay and can be reverted with {@link #undoLast}. */
    public Optional<JobProgress> cancel(UUID owner, WorldAccess world) {
        Job job = jobs.get(owner);
        if (job == null) {
            return Optional.empty();
        }
        if (job.kind == JobKind.UNDO) {
            throw new IllegalStateException("An undo in progress cannot be cancelled.");
        }
        job.state = JobState.CANCELLED;
        job.message = "cancelled by owner";
        finish(job, world);
        return Optional.of(job.progress());
    }

    /**
     * Pauses the owner's queued or running job. A paused job keeps its place in the queue but receives no budget and
     * releases its chunk tickets until {@link #resume} is called.
     */
    public Optional<JobProgress> pause(UUID owner, WorldAccess world) {
        Job job = jobs.get(owner);
        if (job == null) {
            return Optional.empty();
        }
        if (job.state != JobState.PAUSED) {
            job.stateBeforePause = job.state;
            job.state = JobState.PAUSED;
            job.waitingForChunks = false;
            job.releaseAll(world);
        }
        return Optional.of(job.progress());
    }

    /** Resumes a job paused with {@link #pause}. */
    public Optional<JobProgress> resume(UUID owner) {
        Job job = jobs.get(owner);
        if (job == null) {
            return Optional.empty();
        }
        if (job.state == JobState.PAUSED) {
            job.state = job.stateBeforePause == null ? JobState.QUEUED : job.stateBeforePause;
            job.stateBeforePause = null;
        }
        return Optional.of(job.progress());
    }

    /** Active/queued job of the owner or, if none, the last finished one. */
    public Optional<JobProgress> progress(UUID owner) {
        Job job = jobs.get(owner);
        if (job != null) {
            return Optional.of(job.progress());
        }
        return Optional.ofNullable(lastFinished.get(owner));
    }

    public boolean isBusy(UUID owner) {
        return jobs.containsKey(owner);
    }

    public List<JobProgress> snapshot() {
        List<JobProgress> result = new ArrayList<>(jobs.size());
        for (Job job : jobs.values()) {
            result.add(job.progress());
        }
        return result;
    }

    public TickReport tick(WorldAccess world) {
        if (jobs.isEmpty()) {
            return new TickReport(0, 0, 0);
        }
        long deadline = settings.tickBudgetMillis() > 0
            ? System.nanoTime() + settings.tickBudgetMillis() * 1_000_000L
            : Long.MAX_VALUE;
        List<Job> active = new ArrayList<>(settings.maxConcurrentJobs());
        for (Job job : jobs.values()) {
            if (active.size() >= settings.maxConcurrentJobs()) {
                break;
            }
            if (job.state != JobState.PAUSED) {
                active.add(job);
            }
        }
        if (active.isEmpty()) {
            return new TickReport(0, 0, jobs.size());
        }
        int blocks = Math.max(1, settings.blocksPerTick() / active.size());
        int sections = Math.max(1, settings.sectionsPerTick() / active.size());
        long changed = 0;
        int completed = 0;
        for (Job job : active) {
            if (job.state == JobState.QUEUED) {
                job.state = JobState.RUNNING;
                Bounds bounds = job instanceof BuildJob build ? build.worldBounds() : null;
                for (BuildListener listener : listeners) {
                    listener.onJobStarted(job.progress(), bounds);
                }
            }
            long before = job.blocksChanged;
            boolean done;
            try {
                job.ticks++;
                done = job.step(world, new TickBudget(blocks, sections, deadline), this);
            } catch (RuntimeException ex) {
                job.state = JobState.FAILED;
                job.message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                done = true;
            }
            changed += job.blocksChanged - before;
            if (done) {
                if (job.state == JobState.RUNNING) {
                    job.state = JobState.COMPLETED;
                }
                finish(job, world);
                completed++;
            }
        }
        return new TickReport(changed, completed, jobs.size());
    }

    void sectionCompleted(BuildJob job, Bounds worldBounds) {
        if (listeners.isEmpty()) {
            return;
        }
        JobProgress progress = job.progress();
        for (BuildListener listener : listeners) {
            listener.onSectionCompleted(progress, worldBounds);
        }
    }

    private void finish(Job job, WorldAccess world) {
        jobs.remove(job.owner);
        job.close(world);
        if (job instanceof BuildJob build) {
            UndoJournal journal = build.journal();
            journal.seal();
            UndoJournal previous = history.put(job.owner, journal);
            historyNames.put(job.owner, job.name);
            if (previous != null) {
                previous.discard();
            }
        } else if (job instanceof UndoJob undo) {
            if (job.state == JobState.COMPLETED) {
                undo.journal().discard();
            } else {
                history.put(job.owner, undo.journal());
                historyNames.put(job.owner, job.name.replaceFirst("^undo ", ""));
            }
        }
        JobProgress progress = job.progress();
        lastFinished.put(job.owner, progress);
        for (BuildListener listener : listeners) {
            listener.onJobFinished(progress);
        }
    }

    private void requireAvailable(UUID owner) {
        Job existing = jobs.get(owner);
        if (existing != null) {
            throw new IllegalStateException("A " + existing.kind.name().toLowerCase(java.util.Locale.ROOT)
                + " is already in progress for you (" + existing.name + "). Use /architect progress or /architect cancel.");
        }
        if (jobs.size() >= settings.maxQueuedJobs()) {
            throw new IllegalStateException("The build queue is full (" + settings.maxQueuedJobs() + " jobs). Try again later.");
        }
    }

    public record TickReport(long blocksChanged, int completedJobs, int remainingJobs) {
    }
}
