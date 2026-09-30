package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;

import java.nio.file.Path;

/**
 * Server-side limits, overridable with {@code -Darchitect.<name>=<value>} JVM properties.
 */
public final class ArchitectConfig {
    public static final int BLOCKS_PER_TICK = clamp(Integer.getInteger("architect.blocksPerTick", 64), 1, 16_384);
    public static final int SECTIONS_PER_TICK = clamp(Integer.getInteger("architect.sectionsPerTick", 8), 1, 256);
    public static final int TICK_BUDGET_MILLIS = clamp(Integer.getInteger("architect.tickBudgetMillis", 20), 0, 45);
    public static final int MAX_QUEUED_JOBS = clamp(Integer.getInteger("architect.maxQueuedJobs", 16), 1, 256);
    public static final int MAX_CONCURRENT_JOBS = clamp(Integer.getInteger("architect.maxConcurrentJobs", 2), 1, 16);
    public static final int MAX_EXPORT_SECTIONS = clamp(Integer.getInteger("architect.maxExportSections", 1_000_000), 1, 4_000_000);
    public static final int MAX_IMAGE_SCALE = clamp(Integer.getInteger("architect.maxImageScale", 16), 1, 24);

    private ArchitectConfig() {
    }

    public static BuildSettings buildSettings(Path journalDirectory) {
        BuildSettings defaults = BuildSettings.defaults();
        return new BuildSettings(BLOCKS_PER_TICK, SECTIONS_PER_TICK, TICK_BUDGET_MILLIS, MAX_QUEUED_JOBS, MAX_CONCURRENT_JOBS,
            defaults.maxSectionsPerJob(), defaults.journalMemoryEntries(), journalDirectory);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
