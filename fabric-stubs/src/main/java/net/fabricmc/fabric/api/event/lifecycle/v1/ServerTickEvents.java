package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

public final class ServerTickEvents {
    public static final Event<StartTick> START_SERVER_TICK = new Event<>();
    public static final Event<EndTick> END_SERVER_TICK = new Event<>();
    public static final Event<StartWorldTick> START_WORLD_TICK = new Event<>();
    public static final Event<EndWorldTick> END_WORLD_TICK = new Event<>();

    private ServerTickEvents() {}

    @FunctionalInterface
    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface StartWorldTick {
        void onStartWorldTick(ServerWorld world);
    }

    @FunctionalInterface
    public interface EndWorldTick {
        void onEndWorldTick(ServerWorld world);
    }
}
