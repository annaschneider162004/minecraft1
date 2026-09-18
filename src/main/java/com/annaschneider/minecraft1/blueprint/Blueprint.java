package com.annaschneider.minecraft1.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;

public final class Blueprint {
    private final String name;
    private final List<BlueprintBlock> blocks = new ArrayList<>();

    public Blueprint(String name) { this.name = name; }
    public String name() { return name; }
    public List<BlueprintBlock> blocks() { return blocks; }
    public Blueprint add(int x, int y, int z, Block block) {
        blocks.add(new BlueprintBlock(new BlockPos(x, y, z), block));
        return this;
    }
    public Blueprint rotate(int degrees) {
        int turns = ((degrees % 360) + 360) % 360 / 90;
        Blueprint result = new Blueprint(name + "-rotated");
        for (BlueprintBlock item : blocks) {
            BlockPos p = item.relativePos();
            int x = p.getX(), z = p.getZ();
            for (int i = 0; i < turns; i++) { int next = -z; z = x; x = next; }
            result.blocks.add(new BlueprintBlock(new BlockPos(x, p.getY(), z), item.block()));
        }
        return result;
    }
}
