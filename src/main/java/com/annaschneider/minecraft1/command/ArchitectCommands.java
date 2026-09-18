package com.annaschneider.minecraft1.command;

import com.annaschneider.minecraft1.blueprint.Blueprint;
import com.annaschneider.minecraft1.blueprint.Templates;
import com.annaschneider.minecraft1.build.BuildManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ArchitectCommands {
    private ArchitectCommands() { }
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("architect")
            .then(Commands.literal("build").then(Commands.argument("template", StringArgumentType.word()).executes(c -> build(c.getSource(), StringArgumentType.getString(c, "template"), 0))))
            .then(Commands.literal("rotate").then(Commands.argument("degrees", IntegerArgumentType.integer(0, 270)).executes(c -> build(c.getSource(), "house", IntegerArgumentType.getInteger(c, "degrees")))))
            .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
            .then(Commands.literal("help").executes(c -> help(c.getSource()))));
    }
    private static int build(CommandSourceStack source, String name, int rotation) throws Exception {
        ServerPlayer p = source.getPlayerOrException(); Blueprint b = Templates.create(name);
        if (b == null) { source.sendFailure(Component.literal("Unknown template. Use house, castle, or village.")); return 0; }
        if (rotation != 0) b = b.rotate(rotation);
        if (!BuildManager.start(p, b, p.blockPosition().above())) { source.sendFailure(Component.literal("A build is already in progress.")); return 0; }
        source.sendSuccess(() -> Component.literal("Architect: building " + name + "..."), true); return 1;
    }
    private static int undo(CommandSourceStack source) throws Exception {
        if (BuildManager.undo(source.getPlayerOrException())) { source.sendSuccess(() -> Component.literal("Architect: last build undone."), true); return 1; }
        source.sendFailure(Component.literal("There is no completed build to undo.")); return 0;
    }
    private static int help(CommandSourceStack source) { source.sendSuccess(() -> Component.literal("/architect build <house|castle|village> | /architect rotate <0|90|180|270> | /architect undo"), false); return 1; }
}
