package com.annaschneider.minecraft1.domain;

import java.util.Objects;

public record BlueprintBlock(Vec3i position, String blockId) {
    public BlueprintBlock {
        Objects.requireNonNull(position, "position");
        blockId = BlueprintValidation.requireBlockId(blockId);
    }
}
