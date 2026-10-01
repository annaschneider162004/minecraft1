package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleEvents {
    public static final Event<ServerStarting> SERVER_STARTING = new Event<>();
    public static final Event<ServerStarted> SERVER_STARTED = new Event<>();
    public static final Event<ServerStopping> SERVER_STOPPING = new Event<>();
    public static final Event<ServerStopped> SERVER_STOPPED = new Event<>();

    private ServerLifecycleEvents() {}

    @FunctionalInterface
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }
}
