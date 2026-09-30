package com.annaschneider.minecraft1.largebuild.engine;

/**
 * Per-job work allowance for a single tick.
 */
final class TickBudget {
    int blocks;
    int sections;
    private final long deadlineNanos;
    private int checks;

    TickBudget(int blocks, int sections, long deadlineNanos) {
        this.blocks = blocks;
        this.sections = sections;
        this.deadlineNanos = deadlineNanos;
    }

    boolean timeExceeded() {
        if (deadlineNanos == Long.MAX_VALUE) {
            return false;
        }
        if ((++checks & 63) != 0) {
            return false;
        }
        return System.nanoTime() > deadlineNanos;
    }
}
