package com.annaschneider.minecraft1.domain;

import java.util.List;

public final class BlueprintValidation {
    private BlueprintValidation() {
    }

    public static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Blueprint name must not be blank.");
        }
        return name.trim();
    }

    public static String requireBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("Block id must not be blank.");
        }
        String trimmed = blockId.trim().toLowerCase();
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    public static void validateBlueprint(Blueprint blueprint, int maxBlocks, int maxAbsCoordinate) {
        if (blueprint.blocks().size() > maxBlocks) {
            throw new IllegalArgumentException("Blueprint exceeds max block limit: " + maxBlocks);
        }
        for (BlueprintBlock block : blueprint.blocks()) {
            Vec3i p = block.position();
            if (Math.abs(p.x()) > maxAbsCoordinate || Math.abs(p.y()) > maxAbsCoordinate || Math.abs(p.z()) > maxAbsCoordinate) {
                throw new IllegalArgumentException("Blueprint coordinate out of bounds at " + p);
            }
        }
    }

    public static List<BlueprintBlock> requireBlocks(List<BlueprintBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("Blueprint must contain at least one block.");
        }
        return List.copyOf(blocks);
    }
}
