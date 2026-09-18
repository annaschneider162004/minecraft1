package com.annaschneider.minecraft1.build;

import com.annaschneider.minecraft1.blueprint.Blueprint;
import com.annaschneider.minecraft1.blueprint.BlueprintBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class BuildManager {
    private static final Map<UUID, BuildSession> ACTIVE = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> HISTORY = new HashMap<>();
    public static int BLOCKS_PER_TICK = 64;
    private BuildManager() { }

    public static boolean start(ServerPlayer player, Blueprint blueprint, BlockPos origin) {
        if (ACTIVE.containsKey(player.getUUID())) return false;
        ACTIVE.put(player.getUUID(), new BuildSession(player, blueprint, origin));
        return true;
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, BuildSession>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            BuildSession session = it.next().getValue();
            if (session.tick(BLOCKS_PER_TICK)) {
                HISTORY.put(session.player().getUUID(), session.placed());
                session.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("Architect: build complete (" + session.placed().size() + " blocks)."));
                it.remove();
            }
        }
    }

    public static boolean undo(ServerPlayer player) {
        if (ACTIVE.containsKey(player.getUUID())) return false;
        List<BlockPos> positions = HISTORY.remove(player.getUUID());
        if (positions == null) return false;
        for (BlockPos pos : positions) player.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }
}
