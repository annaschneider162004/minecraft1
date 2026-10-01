package com.annaschneider.minecraft1.mod.npc;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.npc.AgentRole;
import com.annaschneider.minecraft1.largebuild.npc.AgentTask;
import com.annaschneider.minecraft1.largebuild.npc.BuildAgent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

/**
 * A visible worker: a vanilla mob (a villager) that is repositioned around the active construction frontier and looks
 * at the blocks being placed. It is purely cosmetic - it has no AI, never picks anything up and never writes a block;
 * the {@link com.annaschneider.minecraft1.largebuild.engine.BuildQueue} remains the only writer.
 */
public final class VillagerWorker implements BuildAgent {
    /** Never dig more than this far down to find standing ground. */
    private static final int MAX_GROUND_SEARCH = 32;

    private final MobEntity entity;
    private final ServerWorld world;
    private final AgentRole role;
    private final String name;
    private AgentTask task;

    VillagerWorker(MobEntity entity, ServerWorld world, AgentRole role, String name) {
        this.entity = entity;
        this.world = world;
        this.role = role;
        this.name = name;
    }

    public MobEntity entity() {
        return entity;
    }

    public ServerWorld world() {
        return world;
    }

    @Override
    public UUID id() {
        return entity.getUuid();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AgentRole role() {
        return role;
    }

    @Override
    public Optional<Vec3i> position() {
        if (entity.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(new Vec3i((int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()),
            (int) Math.floor(entity.getZ())));
    }

    @Override
    public boolean isIdle() {
        return task == null;
    }

    @Override
    public void assign(AgentTask task) {
        this.task = task;
    }

    public Optional<AgentTask> task() {
        return Optional.ofNullable(task);
    }

    @Override
    public void moveTo(Vec3i position, Vec3i lookAt) {
        if (entity.isRemoved() || position == null) {
            return;
        }
        // cosmetic only: never load a chunk just to place a worker
        if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            return;
        }
        int y = groundBelow(position);
        double x = position.x() + 0.5;
        double z = position.z() + 0.5;
        float yaw = entity.getYaw();
        float pitch = 0;
        if (lookAt != null) {
            double dx = lookAt.x() + 0.5 - x;
            double dy = lookAt.y() + 0.5 - (y + 1.6);
            double dz = lookAt.z() + 0.5 - z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.001, horizontal)));
        }
        entity.refreshPositionAndAngles(x, y, z, yaw, pitch);
        entity.setHeadYaw(yaw);
        entity.swingHand(Hand.MAIN_HAND);
    }

    /** Removes the worker from the world; safe to call twice. */
    public void remove() {
        if (!entity.isRemoved()) {
            entity.discard();
        }
    }

    /** First free spot at or below {@code target}, so workers stand on the build instead of inside it. */
    private int groundBelow(Vec3i target) {
        int y = Math.min(world.getTopY() - 2, Math.max(world.getBottomY() + 1, target.y()));
        for (int i = 0; i < MAX_GROUND_SEARCH && y > world.getBottomY() + 1; i++) {
            if (!world.isAir(new BlockPos(target.x(), y - 1, target.z()))) {
                return y;
            }
            y--;
        }
        return Math.min(world.getTopY() - 2, Math.max(world.getBottomY() + 1, target.y()));
    }
}
