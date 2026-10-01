package net.minecraft.entity;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.world.World;

import java.util.function.Function;

/** Compile-time stub of {@code net.minecraft.entity.EntityType}. */
public final class EntityType<T extends Entity> {
    public static final EntityType<VillagerEntity> VILLAGER = new EntityType<>(VillagerEntity::new);

    private final Function<World, T> factory;

    private EntityType(Function<World, T> factory) {
        this.factory = factory;
    }

    public T create(World world) {
        return factory.apply(world);
    }
}
