package com.mojang.brigadier.arguments;

import com.mojang.brigadier.context.CommandContext;

public class StringArgumentType implements ArgumentType<String> {
    private final String type;

    private StringArgumentType(String type) {
        this.type = type;
    }

    public static StringArgumentType string() {
        return new StringArgumentType("string");
    }

    public static StringArgumentType greedyString() {
        return new StringArgumentType("greedy");
    }

    public static String getString(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }
}
