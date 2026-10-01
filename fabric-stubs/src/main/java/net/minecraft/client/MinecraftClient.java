package net.minecraft.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class MinecraftClient {
    private static final MinecraftClient INSTANCE = new MinecraftClient();

    public static MinecraftClient getInstance() {
        return INSTANCE;
    }

    public void execute(Runnable runnable) {
        runnable.run();
    }

    public <V> CompletableFuture<V> submit(Supplier<V> task) {
        return CompletableFuture.completedFuture(task.get());
    }
}
