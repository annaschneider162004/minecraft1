package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.Vec3i;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BuildQueueManager {
    private final Map<UUID, BuildSession> active = new HashMap<>();
    private final Map<UUID, List<PlacedBlock>> history = new HashMap<>();

    public boolean start(UUID playerId, Blueprint blueprint, Vec3i origin) {
        if (active.containsKey(playerId)) {
            return false;
        }
        active.put(playerId, new BuildSession(blueprint, origin));
        return true;
    }

    public TickSummary tick(BlockWorld world) {
        int completed = 0;
        int progressed = 0;
        Iterator<Map.Entry<UUID, BuildSession>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BuildSession> entry = iterator.next();
            BuildSession session = entry.getValue();
            int before = session.currentIndex();
            if (session.tick(ArchitectConfig.BLOCKS_PER_TICK, world)) {
                history.put(entry.getKey(), List.copyOf(session.placed()));
                iterator.remove();
                completed++;
            }
            progressed += (session.currentIndex() - before);
        }
        return new TickSummary(progressed, completed);
    }

    public boolean undo(UUID playerId, BlockWorld world) {
        if (active.containsKey(playerId)) {
            return false;
        }
        List<PlacedBlock> placed = history.remove(playerId);
        if (placed == null || placed.isEmpty()) {
            return false;
        }
        for (int i = placed.size() - 1; i >= 0; i--) {
            PlacedBlock block = placed.get(i);
            world.setBlock(block.position(), block.previousState());
        }
        return true;
    }

    public record TickSummary(int progressedBlocks, int completedSessions) {
    }
}
