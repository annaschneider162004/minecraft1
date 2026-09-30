package com.annaschneider.minecraft1.largebuild.engine;

import java.util.UUID;

/**
 * Restores the states recorded in an {@link UndoJournal}, bounded per tick like a build.
 */
final class UndoJob extends Job {
    private final UndoJournal journal;
    private final UndoJournal.Entry entry = new UndoJournal.Entry();
    private UndoJournal.Reader reader;
    private boolean pending;

    UndoJob(long id, UUID owner, String name, UndoJournal journal) {
        super(id, owner, "undo " + name, JobKind.UNDO);
        this.journal = journal;
    }

    UndoJournal journal() {
        return journal;
    }

    @Override
    long unitsTotal() {
        return journal.size();
    }

    @Override
    boolean step(WorldAccess world, TickBudget budget, BuildQueue queue) {
        if (reader == null) {
            reader = journal.openReader();
        }
        while (budget.blocks > 0 && !budget.timeExceeded()) {
            if (!pending) {
                if (!reader.next(entry)) {
                    return true;
                }
                pending = true;
            }
            if (!holdChunks(world, entry.x, entry.z, entry.x, entry.z)) {
                return false;
            }
            world.setBlock(entry.x, entry.y, entry.z, entry.previous);
            pending = false;
            unitsDone++;
            blocksChanged++;
            budget.blocks--;
        }
        return false;
    }

    @Override
    void close(WorldAccess world) {
        super.close(world);
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }
}
