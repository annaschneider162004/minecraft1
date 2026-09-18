package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBlock;
import com.annaschneider.minecraft1.domain.Vec3i;

import java.util.ArrayList;
import java.util.List;

final class BuildSession {
    private final Blueprint blueprint;
    private final Vec3i origin;
    private final List<PlacedBlock> placed = new ArrayList<>();
    private int index;

    BuildSession(Blueprint blueprint, Vec3i origin) {
        this.blueprint = blueprint;
        this.origin = origin;
    }

    boolean tick(int amount, BlockWorld world) {
        int end = Math.min(index + amount, blueprint.blocks().size());
        while (index < end) {
            BlueprintBlock block = blueprint.blocks().get(index++);
            Vec3i target = origin.add(block.position());
            String previous = world.getBlock(target);
            String next = block.blockId();
            if (!previous.equals(next)) {
                world.setBlock(target, next);
                placed.add(new PlacedBlock(target, previous, next));
            }
        }
        return index >= blueprint.blocks().size();
    }

    int currentIndex() {
        return index;
    }

    int totalBlocks() {
        return blueprint.blocks().size();
    }

    List<PlacedBlock> placed() {
        return placed;
    }
}
