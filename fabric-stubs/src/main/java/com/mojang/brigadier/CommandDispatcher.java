package com.mojang.brigadier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;

public class CommandDispatcher<S> {
    private final Map<String, LiteralArgumentBuilder<S>> registeredCommands = new HashMap<>();

    public void register(LiteralArgumentBuilder<S> command) {
        registeredCommands.put(command.getLiteral(), command);
    }

    public int execute(String input, S source) throws CommandSyntaxException {
        String[] parts = input.trim().split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            return 0;
        }
        String root = parts[0];
        LiteralArgumentBuilder<S> cmd = registeredCommands.get(root);
        if (cmd == null) {
            return 0;
        }
        CommandContext<S> ctx = new CommandContext<>(source);
        if (parts.length > 1) {
            ctx.setArgument("command", parts[1]);
        }
        if (cmd.getCommand() != null) {
            return cmd.getCommand().run(ctx);
        }
        return 0;
    }

    public Map<String, LiteralArgumentBuilder<S>> getRegisteredCommands() {
        return registeredCommands;
    }
}
