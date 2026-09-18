package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.BlueprintValidation;

import java.util.Set;

public final class BlockCatalog {
    private static final Set<String> SUPPORTED = Set.of(
        "minecraft:air", "minecraft:stone", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks",
        "minecraft:mossy_cobblestone", "minecraft:polished_andesite", "minecraft:oak_planks", "minecraft:spruce_planks",
        "minecraft:dark_oak_planks", "minecraft:birch_planks", "minecraft:cobblestone", "minecraft:dirt_path",
        "minecraft:grass_block", "minecraft:farmland", "minecraft:oak_log", "minecraft:oak_leaves",
        "minecraft:water", "minecraft:blue_wool", "minecraft:yellow_wool", "minecraft:green_wool", "minecraft:cyan_wool",
        "minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone", "minecraft:sandstone_slab",
        "minecraft:quartz_block", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_slab", "minecraft:mud_bricks"
    );

    private BlockCatalog() {
    }

    public static boolean isSupported(String blockId) {
        return SUPPORTED.contains(BlueprintValidation.requireBlockId(blockId));
    }
}
