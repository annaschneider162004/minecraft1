package net.minecraft.registry;

import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.Set;

public interface Registry<T> {
    T get(Identifier id);

    Identifier getId(T entry);

    boolean containsId(Identifier id);

    Optional<T> getOrEmpty(Identifier id);

    Set<Identifier> getIds();
}
