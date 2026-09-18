package com.annaschneider.minecraft1.build;

import com.annaschneider.minecraft1.blueprint.Blueprint;
import com.annaschneider.minecraft1.blueprint.BlueprintBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public final class BuildSession {
    private final ServerPlayer player; private final Blueprint blueprint; private final BlockPos origin;
    private final List<BlockPos> placed = new ArrayList<>(); private int index;
    public BuildSession(ServerPlayer player, Blueprint blueprint, BlockPos origin) { this.player=player; this.blueprint=blueprint; this.origin=origin; }
    public boolean tick(int amount) {
        int end = Math.min(index + amount, blueprint.blocks().size());
        while (index < end) {
            BlueprintBlock item = blueprint.blocks().get(index++);
            BlockPos pos = origin.offset(item.relativePos());
            player.serverLevel().setBlock(pos, item.block().defaultBlockState(), 3);
            placed.add(pos);
        }
        if (index % Math.max(1, amount * 4) == 0 || index == blueprint.blocks().size()) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Architect: " + index + "/" + blueprint.blocks().size()));
        return index >= blueprint.blocks().size();
    }
    public ServerPlayer player() { return player; }
    public List<BlockPos> placed() { return placed; }
}
