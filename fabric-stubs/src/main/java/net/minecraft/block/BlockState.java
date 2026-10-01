package net.minecraft.block;

public class BlockState {
    private final Block block;

    public BlockState(Block block) {
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    public boolean isAir() {
        return block == Blocks.AIR;
    }
}
