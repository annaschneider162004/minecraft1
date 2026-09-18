package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.Vec3i;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryBlockWorld implements BlockWorld {
    private final Map<Vec3i, String> blocks = new HashMap<>();

    @Override
    public String getBlock(Vec3i position) {
        return blocks.getOrDefault(position, "minecraft:air");
    }

    @Override
    public void setBlock(Vec3i position, String blockId) {
        if ("minecraft:air".equals(blockId)) {
            blocks.remove(position);
            return;
        }
        blocks.put(position, blockId);
    }
}
