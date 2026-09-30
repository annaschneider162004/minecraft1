package com.annaschneider.minecraft1.largebuild.engine;

import java.nio.file.Path;

/**
 * Tick budgets and limits of the build queue.
 *
 * @param blocksPerTick        cells processed per server tick across all running jobs
 * @param sectionsPerTick      16^3 sections that may be generated/loaded per tick
 * @param tickBudgetMillis     wall-clock cap per tick (0 disables)
 * @param maxQueuedJobs        max queued + running jobs
 * @param maxConcurrentJobs    jobs that share the per-tick budget simultaneously
 * @param maxSectionsPerJob    upper bound on sections for one blueprint
 * @param journalMemoryEntries undo entries kept in memory before spilling to {@code journalDirectory}
 * @param journalDirectory     directory for spilled undo journals, or {@code null} to keep journals in memory
 */
public record BuildSettings(
    int blocksPerTick,
    int sectionsPerTick,
    int tickBudgetMillis,
    int maxQueuedJobs,
    int maxConcurrentJobs,
    long maxSectionsPerJob,
    int journalMemoryEntries,
    Path journalDirectory
) {
    public static final int MAX_BLOCKS_PER_TICK = 16_384;

    public BuildSettings {
        blocksPerTick = clamp(blocksPerTick, 1, MAX_BLOCKS_PER_TICK);
        sectionsPerTick = clamp(sectionsPerTick, 1, 256);
        tickBudgetMillis = clamp(tickBudgetMillis, 0, 50);
        maxQueuedJobs = clamp(maxQueuedJobs, 1, 256);
        maxConcurrentJobs = clamp(maxConcurrentJobs, 1, 16);
        maxSectionsPerJob = Math.max(1, Math.min(maxSectionsPerJob, 10_000_000L));
        journalMemoryEntries = clamp(journalMemoryEntries, 1_024, 10_000_000);
    }

    public static BuildSettings defaults() {
        return new BuildSettings(64, 8, 20, 16, 2, 4_000_000L, 262_144, null);
    }

    public BuildSettings withBlocksPerTick(int value) {
        return new BuildSettings(value, sectionsPerTick, tickBudgetMillis, maxQueuedJobs, maxConcurrentJobs, maxSectionsPerJob, journalMemoryEntries, journalDirectory);
    }

    public BuildSettings withJournal(int memoryEntries, Path directory) {
        return new BuildSettings(blocksPerTick, sectionsPerTick, tickBudgetMillis, maxQueuedJobs, maxConcurrentJobs, maxSectionsPerJob, memoryEntries, directory);
    }

    public BuildSettings withQueueLimits(int queued, int concurrent) {
        return new BuildSettings(blocksPerTick, sectionsPerTick, tickBudgetMillis, queued, concurrent, maxSectionsPerJob, journalMemoryEntries, journalDirectory);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
