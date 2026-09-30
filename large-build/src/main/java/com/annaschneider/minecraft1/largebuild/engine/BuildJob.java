package com.annaschneider.minecraft1.largebuild.engine;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.BlockPalette;
import com.annaschneider.minecraft1.largebuild.blueprint.BlockSection;
import com.annaschneider.minecraft1.largebuild.blueprint.BlueprintSource;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionCursor;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionKey;

import java.util.UUID;

/**
 * Streams a {@link BlueprintSource} into the world one section at a time. Only a single 16^3 buffer is held.
 */
final class BuildJob extends Job {
    private final BlueprintSource source;
    private final Vec3i origin;
    private final UndoJournal journal;
    private final BlockSection section = new BlockSection();
    private final long total;
    private SectionCursor cursor;
    private boolean hasSection;
    private boolean prepared;
    private int cell;

    BuildJob(long id, UUID owner, BlueprintSource source, Vec3i origin, UndoJournal journal) {
        super(id, owner, source.name(), JobKind.BUILD);
        this.source = source;
        this.origin = origin;
        this.journal = journal;
        this.total = source.sectionCount();
    }

    UndoJournal journal() {
        return journal;
    }

    Bounds worldBounds() {
        return source.bounds().translated(origin.x(), origin.y(), origin.z());
    }

    @Override
    long unitsTotal() {
        return total;
    }

    @Override
    boolean step(WorldAccess world, TickBudget budget, BuildQueue queue) {
        if (cursor == null) {
            cursor = source.openCursor();
        }
        BlockPalette palette = source.palette();
        while (budget.blocks > 0 && !budget.timeExceeded()) {
            if (!hasSection) {
                if (budget.sections <= 0) {
                    return false;
                }
                budget.sections--;
                if (!cursor.next(section)) {
                    return true;
                }
                if (section.isEmpty()) {
                    unitsDone++;
                    continue;
                }
                hasSection = true;
                prepared = false;
                cell = 0;
            }
            SectionKey key = section.key();
            int baseX = origin.x() + key.minX();
            int baseY = origin.y() + key.minY();
            int baseZ = origin.z() + key.minZ();
            if (!prepared) {
                if (!holdChunks(world, baseX, baseZ, baseX + 15, baseZ + 15)) {
                    return false;
                }
                prepared = true;
            }
            while (cell < SectionKey.VOLUME && budget.blocks > 0) {
                short value = section.get(cell);
                if (value != BlockPalette.EMPTY) {
                    int x = baseX + BlockSection.localX(cell);
                    int y = baseY + BlockSection.localY(cell);
                    int z = baseZ + BlockSection.localZ(cell);
                    String next = palette.blockId(value);
                    String previous = world.getBlock(x, y, z);
                    if (!next.equals(previous)) {
                        journal.record(x, y, z, previous);
                        world.setBlock(x, y, z, next);
                        blocksChanged++;
                    } else {
                        blocksUnchanged++;
                    }
                    budget.blocks--;
                }
                cell++;
            }
            if (cell >= SectionKey.VOLUME) {
                hasSection = false;
                unitsDone++;
                queue.sectionCompleted(this, new Bounds(baseX, baseY, baseZ, baseX + 15, baseY + 15, baseZ + 15));
            }
        }
        return false;
    }

    @Override
    void close(WorldAccess world) {
        super.close(world);
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }
}
