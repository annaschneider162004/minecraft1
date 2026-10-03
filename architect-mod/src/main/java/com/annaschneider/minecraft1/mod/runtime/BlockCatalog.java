package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.BlueprintValidation;

import java.util.Collection;
import java.util.Set;

public final class BlockCatalog {
    private static final Set<String> SUPPORTED = Set.of(
        "minecraft:air", "minecraft:stone", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks",
        "minecraft:mossy_cobblestone", "minecraft:polished_andesite", "minecraft:oak_planks", "minecraft:spruce_planks",
        "minecraft:dark_oak_planks", "minecraft:birch_planks", "minecraft:cobblestone", "minecraft:dirt_path",
        "minecraft:grass_block", "minecraft:farmland", "minecraft:oak_log", "minecraft:oak_leaves",
        "minecraft:water", "minecraft:blue_wool", "minecraft:yellow_wool", "minecraft:green_wool", "minecraft:cyan_wool",
        "minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone", "minecraft:sandstone_slab",
        "minecraft:quartz_block", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_slab", "minecraft:mud_bricks",
        // large-build scene generators
        "minecraft:smooth_quartz", "minecraft:quartz_pillar", "minecraft:quartz_slab", "minecraft:glass",
        "minecraft:gold_block", "minecraft:yellow_terracotta", "minecraft:lantern", "minecraft:andesite",
        "minecraft:mossy_stone_bricks", "minecraft:cherry_log", "minecraft:cherry_leaves", "minecraft:lily_pad",
        "minecraft:moss_block", "minecraft:gravel", "minecraft:pink_tulip", "minecraft:allium", "minecraft:azure_bluet",
        "minecraft:lily_of_the_valley", "minecraft:oxeye_daisy", "minecraft:white_wool", "minecraft:coarse_dirt",
        "minecraft:dirt",
        // style-aware ground-based layouts
        "minecraft:purple_concrete", "minecraft:sandstone", "minecraft:red_sandstone",
        "minecraft:bricks", "minecraft:iron_block", "minecraft:gray_concrete", "minecraft:stone_slab",
        "minecraft:dead_bush", "minecraft:poppy"
    );

    private BlockCatalog() {
    }

    /** Every block id the generators may place (all vanilla Minecraft 1.20.1 blocks). */
    public static Set<String> supportedIds() {
        return SUPPORTED;
    }

    public static boolean isSupported(String blockId) {
        return SUPPORTED.contains(BlueprintValidation.requireBlockId(blockId));
    }

    /** Throws with a user-facing message if any id is not supported. */
    public static void requireSupported(Collection<String> blockIds) {
        for (String id : blockIds) {
            if (!isSupported(id)) {
                throw new IllegalArgumentException("Unsupported block for foundation: " + BlueprintValidation.requireBlockId(id));
            }
        }
    }
}
