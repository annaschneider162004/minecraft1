package com.annaschneider.minecraft1.mod.npc;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.npc.AgentRole;
import com.annaschneider.minecraft1.largebuild.npc.BuildAgent;
import com.annaschneider.minecraft1.largebuild.npc.BuildCrewCoordinator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Creates and removes the visible worker mobs for {@link BuildCrewCoordinator}. Every worker is tagged with
 * {@link #WORKER_TAG} so {@link #removeOrphans(MinecraftServer)} can find workers left behind by a crash or a reload
 * without ever touching entities of other mods or of the player.
 * <p>
 * All entity operations run on the server thread, inside the build queue tick.
 */
public final class VillagerWorkerFactory implements BuildCrewCoordinator.WorkerFactory {
    /** Scoreboard/command tag that marks every entity this mod created. */
    public static final String WORKER_TAG = "architect_worker";
    private static final Logger LOGGER = Logger.getLogger("com.annaschneider.minecraft1.mod.npc");

    private final MinecraftServer server;

    public VillagerWorkerFactory(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Optional<BuildAgent> create(long jobId, UUID owner, int index, AgentRole role, Vec3i near) {
        ServerWorld world = worldOf(owner);
        if (world == null || near == null || !server.isOnThread()) {
            return Optional.empty();
        }
        // cosmetic NPCs never force chunks to load; they only appear where the build already prepared the world
        if (!world.isChunkLoaded(near.x() >> 4, near.z() >> 4)) {
            return Optional.empty();
        }
        try {
            VillagerEntity villager = EntityType.VILLAGER.create(world);
            if (villager == null) {
                return Optional.empty();
            }
            String name = role.displayName() + " #" + (index + 1);
            configure(villager, name);
            villager.refreshPositionAndAngles(near.x() + 0.5, near.y(), near.z() + 0.5, 0, 0);
            if (!world.spawnEntity(villager)) {
                return Optional.empty();
            }
            VillagerWorker worker = new VillagerWorker(villager, world, role, name);
            worker.moveTo(near, near);
            return Optional.of(worker);
        } catch (RuntimeException ex) {
            LOGGER.warning("[Architect] Could not spawn a builder NPC: " + ex);
            return Optional.empty();
        }
    }

    @Override
    public void dispose(BuildAgent agent) {
        if (agent instanceof VillagerWorker worker) {
            try {
                worker.remove();
            } catch (RuntimeException ex) {
                LOGGER.warning("[Architect] Could not remove a builder NPC: " + ex);
            }
        }
    }

    /** Harmless, non-interactive and clearly labelled: no AI, no loot, no damage, no despawn surprises. */
    private static void configure(MobEntity villager, String name) {
        villager.addCommandTag(WORKER_TAG);
        villager.setCustomName(Text.literal(name));
        villager.setCustomNameVisible(true);
        villager.setAiDisabled(true);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setNoGravity(true);
        villager.setCanPickUpLoot(false);
    }

    /**
     * Removes every worker this mod ever created from the loaded worlds. Used on server start (so a crash cannot leave
     * workers behind) and on server stop.
     */
    public static int removeOrphans(MinecraftServer server) {
        if (server == null || !server.isOnThread()) {
            return 0;
        }
        int removed = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<Entity> doomed = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (entity.getCommandTags().contains(WORKER_TAG)) {
                    doomed.add(entity);
                }
            }
            for (Entity entity : doomed) {
                entity.discard();
                removed++;
            }
        }
        return removed;
    }

    private ServerWorld worldOf(UUID owner) {
        if (server == null || owner == null || server.getPlayerManager() == null) {
            return null;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        return player == null ? null : player.getServerWorld();
    }
}
