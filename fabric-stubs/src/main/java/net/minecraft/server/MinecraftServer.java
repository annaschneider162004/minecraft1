package net.minecraft.server;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class MinecraftServer implements Executor {
    private final PlayerManager playerManager = new PlayerManager();
    private final Map<RegistryKey<World>, ServerWorld> worlds = new HashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private boolean dedicated = false;
    private File runDirectory = new File(".");
    private Thread serverThread = Thread.currentThread();

    public MinecraftServer() {
        ServerWorld overworld = new ServerWorld(this, World.OVERWORLD);
        worlds.put(World.OVERWORLD, overworld);
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ServerWorld getOverworld() {
        return worlds.get(World.OVERWORLD);
    }

    public ServerWorld getWorld(RegistryKey<World> key) {
        return worlds.get(key);
    }

    public Iterable<ServerWorld> getWorlds() {
        return worlds.values();
    }

    public void addWorld(RegistryKey<World> key, ServerWorld world) {
        worlds.put(key, world);
    }

    public boolean isDedicated() {
        return dedicated;
    }

    public void setDedicated(boolean dedicated) {
        this.dedicated = dedicated;
    }

    public File getRunDirectory() {
        return runDirectory;
    }

    public void setRunDirectory(File runDirectory) {
        this.runDirectory = runDirectory;
    }

    public boolean isOnThread() {
        return Thread.currentThread() == serverThread;
    }

    public void setServerThread(Thread thread) {
        this.serverThread = thread;
    }

    @Override
    public void execute(Runnable runnable) {
        if (isOnThread()) {
            runnable.run();
        } else {
            synchronized (tasks) {
                tasks.add(runnable);
            }
        }
    }

    public void runTasks() {
        List<Runnable> copy;
        synchronized (tasks) {
            copy = new ArrayList<>(tasks);
            tasks.clear();
        }
        for (Runnable task : copy) {
            task.run();
        }
    }
}
