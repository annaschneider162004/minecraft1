package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.MinecraftClient;

public final class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();
    public static final Event<StartTick> START_CLIENT_TICK = new Event<>();

    private ClientTickEvents() {}

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftClient client);
    }

    @FunctionalInterface
    public interface StartTick {
        void onStartTick(MinecraftClient client);
    }
}
