package net.minecraft.world;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;

public interface World {
    RegistryKey<World> OVERWORLD = RegistryKey.of(RegistryKey.ofRegistry(new Identifier("dimension")), new Identifier("overworld"));
    RegistryKey<World> NETHER = RegistryKey.of(RegistryKey.ofRegistry(new Identifier("dimension")), new Identifier("the_nether"));
    RegistryKey<World> END = RegistryKey.of(RegistryKey.ofRegistry(new Identifier("dimension")), new Identifier("the_end"));

    BlockState getBlockState(BlockPos pos);

    boolean setBlockState(BlockPos pos, BlockState state, int flags);

    int getBottomY();

    int getTopY();

    WorldBorder getWorldBorder();

    boolean isInBuildLimit(BlockPos pos);

    RegistryKey<World> getRegistryKey();
}
