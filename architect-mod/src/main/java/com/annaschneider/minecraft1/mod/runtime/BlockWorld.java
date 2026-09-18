package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.Vec3i;

public interface BlockWorld {
    String getBlock(Vec3i position);

    void setBlock(Vec3i position, String blockId);
}
