package net.fabricmc.fabric.api.event;

import java.util.ArrayList;
import java.util.List;

public class Event<T> {
    private final List<T> handlers = new ArrayList<>();

    public void register(T handler) {
        handlers.add(handler);
    }

    public List<T> handlers() {
        return handlers;
    }

    public void clear() {
        handlers.clear();
    }
}
