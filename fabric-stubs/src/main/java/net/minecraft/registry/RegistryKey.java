package net.minecraft.registry;

import net.minecraft.util.Identifier;

import java.util.Objects;

public final class RegistryKey<T> {
    private final Identifier registry;
    private final Identifier value;

    private RegistryKey(Identifier registry, Identifier value) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.value = Objects.requireNonNull(value, "value");
    }

    public static <T> RegistryKey<T> of(RegistryKey<? extends Registry<T>> rootKey, Identifier id) {
        return of(rootKey.getValue(), id);
    }

    public static <T> RegistryKey<T> of(Identifier registry, Identifier value) {
        return new RegistryKey<>(registry, value);
    }

    public static <T> RegistryKey<Registry<T>> ofRegistry(Identifier registry) {
        return new RegistryKey<>(new Identifier("root"), registry);
    }

    public Identifier getValue() {
        return value;
    }

    public Identifier getRegistry() {
        return registry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistryKey<?> that)) return false;
        return registry.equals(that.registry) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, value);
    }

    @Override
    public String toString() {
        return "ResourceKey[" + registry + " / " + value + "]";
    }
}
