package net.minecraft.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class MinecraftClient {
    private static final MinecraftClient INSTANCE = new MinecraftClient();

    public ClientWorld world;
    public ClientPlayerEntity player;
    public GameOptions options = new GameOptions();

    private Entity cameraEntity;

    public static MinecraftClient getInstance() {
        return INSTANCE;
    }

    public void execute(Runnable runnable) {
        runnable.run();
    }

    public <V> CompletableFuture<V> submit(Supplier<V> task) {
        return CompletableFuture.completedFuture(task.get());
    }

    public Entity getCameraEntity() {
        return cameraEntity;
    }

    public void setCameraEntity(Entity entity) {
        this.cameraEntity = entity;
    }
}
