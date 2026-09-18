package com.annaschneider.minecraft1.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public record BlueprintBlock(BlockPos relativePos, Block block) { }
