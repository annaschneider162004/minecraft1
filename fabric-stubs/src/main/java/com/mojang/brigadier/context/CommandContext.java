package com.mojang.brigadier.context;

import java.util.HashMap;
import java.util.Map;

public class CommandContext<S> {
    private final S source;
    private final Map<String, Object> arguments = new HashMap<>();

    public CommandContext(S source) {
        this.source = source;
    }

    public S getSource() {
        return source;
    }

    public void setArgument(String name, Object value) {
        arguments.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <V> V getArgument(String name, Class<V> clazz) {
        Object val = arguments.get(name);
        return (V) val;
    }
}
