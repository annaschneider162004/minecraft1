package com.annaschneider.minecraft1.domain;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintBuilder {
    private final String name;
    private final List<BlueprintBlock> blocks = new ArrayList<>();

    BlueprintBuilder(String name) {
        this.name = BlueprintValidation.requireName(name);
    }

    public BlueprintBuilder add(int x, int y, int z, String blockId) {
        blocks.add(new BlueprintBlock(new Vec3i(x, y, z), blockId));
        return this;
    }

    public Blueprint build() {
        return Blueprint.of(name, blocks);
    }
}
