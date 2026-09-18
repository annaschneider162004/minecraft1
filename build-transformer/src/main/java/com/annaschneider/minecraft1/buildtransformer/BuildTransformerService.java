package com.annaschneider.minecraft1.buildtransformer;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBlock;

import java.util.ArrayList;
import java.util.List;

public final class BuildTransformerService {
    public Blueprint transform(TransformOperation operation, String templateId, Blueprint source) {
        List<BlueprintBlock> transformed = new ArrayList<>(source.blocks().size());
        for (BlueprintBlock block : source.blocks()) {
            transformed.add(new BlueprintBlock(block.position(), transformBlock(operation, templateId, block)));
        }
        return Blueprint.of(source.name() + "-" + operation.name().toLowerCase(), transformed);
    }

    private String transformBlock(TransformOperation operation, String templateId, BlueprintBlock block) {
        return switch (operation) {
            case UPGRADE -> upgrade(block.blockId());
            case STYLE -> style(templateId, block.blockId());
            case DAMAGE -> damage(block);
        };
    }

    private String upgrade(String blockId) {
        return switch (blockId) {
            case "minecraft:oak_planks" -> "minecraft:stone_bricks";
            case "minecraft:cobblestone" -> "minecraft:polished_andesite";
            case "minecraft:cut_sandstone", "minecraft:smooth_sandstone" -> "minecraft:quartz_block";
            case "minecraft:spruce_planks" -> "minecraft:dark_oak_planks";
            default -> blockId;
        };
    }

    private String style(String templateId, String blockId) {
        if ("temple".equalsIgnoreCase(templateId)) {
            return switch (blockId) {
                case "minecraft:cut_sandstone", "minecraft:smooth_sandstone" -> "minecraft:deepslate_tiles";
                case "minecraft:sandstone_slab" -> "minecraft:deepslate_tile_slab";
                default -> blockId;
            };
        }
        return switch (blockId) {
            case "minecraft:stone_bricks" -> "minecraft:mud_bricks";
            case "minecraft:cobblestone" -> "minecraft:oak_planks";
            case "minecraft:spruce_planks" -> "minecraft:birch_planks";
            default -> blockId;
        };
    }

    private String damage(BlueprintBlock block) {
        int hash = Math.abs(block.position().x() * 31 + block.position().y() * 17 + block.position().z() * 13);
        if (hash % 11 == 0) {
            return "minecraft:air";
        }
        if (hash % 7 == 0) {
            return switch (block.blockId()) {
                case "minecraft:stone_bricks" -> "minecraft:cracked_stone_bricks";
                case "minecraft:cobblestone" -> "minecraft:mossy_cobblestone";
                default -> block.blockId();
            };
        }
        return block.blockId();
    }
}
