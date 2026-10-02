package com.annaschneider.minecraft1.largebuild.npc;

/**
 * Bounded configuration of the visible builder NPCs. Conservative defaults: a handful of cosmetic workers that are
 * repositioned a few times per second at most.
 *
 * @param enabled               whether worker NPCs are spawned at all
 * @param maxWorkers            hard cap on workers per job (1..12)
 * @param sectionsPerWorker     one extra worker per this many sections (16..100_000)
 * @param updateIntervalSections sections built between two repositionings (1..64)
 */
public record NpcSettings(boolean enabled, int maxWorkers, int sectionsPerWorker, int updateIntervalSections) {
    public NpcSettings {
        maxWorkers = clamp(maxWorkers, 1, 12);
        sectionsPerWorker = clamp(sectionsPerWorker, 16, 100_000);
        updateIntervalSections = clamp(updateIntervalSections, 1, 64);
    }

    public static NpcSettings defaults() {
        return new NpcSettings(true, 4, 64, 2);
    }

    public NpcSettings withEnabled(boolean value) {
        return new NpcSettings(value, maxWorkers, sectionsPerWorker, updateIntervalSections);
    }

    /** Workers for a job of {@code sections} sections: at least one, never more than {@link #maxWorkers()}. */
    public int workersFor(long sections) {
        long wanted = 1 + Math.max(0, sections) / sectionsPerWorker;
        return (int) Math.max(1, Math.min(maxWorkers, wanted));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
