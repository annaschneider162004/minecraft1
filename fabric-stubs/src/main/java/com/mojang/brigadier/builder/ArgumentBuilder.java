package com.mojang.brigadier.builder;

import com.mojang.brigadier.Command;
import java.util.ArrayList;
import java.util.List;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    protected Command<S> command;
    protected final List<ArgumentBuilder<S, ?>> children = new ArrayList<>();

    protected abstract T getThis();

    public T executes(Command<S> command) {
        this.command = command;
        return getThis();
    }

    public Command<S> getCommand() {
        return command;
    }

    public T then(ArgumentBuilder<S, ?> argument) {
        this.children.add(argument);
        return getThis();
    }

    public List<ArgumentBuilder<S, ?>> getChildren() {
        return children;
    }
}
