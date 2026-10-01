package net.minecraft.client.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import java.util.HashMap;
import java.util.Map;

/** Compile-time stub of {@code net.minecraft.client.world.ClientWorld}. */
public class ClientWorld implements World {
    private final RegistryKey<World> registryKey;
    private final WorldBorder worldBorder = new WorldBorder();
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();

    public ClientWorld() {
        this(World.OVERWORLD);
    }

    public ClientWorld(RegistryKey<World> registryKey) {
        this.registryKey = registryKey;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public boolean setBlockState(BlockPos pos, BlockState state, int flags) {
        blocks.put(pos, state);
        return true;
    }

    @Override
    public int getBottomY() {
        return -64;
    }

    @Override
    public int getTopY() {
        return 320;
    }

    @Override
    public WorldBorder getWorldBorder() {
        return worldBorder;
    }

    @Override
    public boolean isInBuildLimit(BlockPos pos) {
        return pos.getY() >= getBottomY() && pos.getY() < getTopY();
    }

    @Override
    public RegistryKey<World> getRegistryKey() {
        return registryKey;
    }
}
