package net.minecraft.server.world;

import java.util.Comparator;

public final class ChunkTicketType<T> {
    private final String name;
    private final Comparator<T> argumentComparator;

    private ChunkTicketType(String name, Comparator<T> argumentComparator) {
        this.name = name;
        this.argumentComparator = argumentComparator;
    }

    public static <T> ChunkTicketType<T> create(String name, Comparator<T> argumentComparator) {
        return new ChunkTicketType<>(name, argumentComparator);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
