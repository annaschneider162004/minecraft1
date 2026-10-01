package com.mojang.brigadier.exceptions;

public class CommandSyntaxException extends Exception {
    public CommandSyntaxException(String message) {
        super(message);
    }
}
