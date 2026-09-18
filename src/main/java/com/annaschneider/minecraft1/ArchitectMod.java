package com.annaschneider.minecraft1;

import com.annaschneider.minecraft1.command.ArchitectCommands;
import com.annaschneider.minecraft1.build.BuildManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(ArchitectMod.MOD_ID)
public final class ArchitectMod {
    public static final String MOD_ID = "architect";

    public ArchitectMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        ArchitectCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) BuildManager.tick();
    }
}
