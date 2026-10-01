package net.minecraft.registry;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Registries {
    public static final Registry<Block> BLOCK = new SimpleRegistry<>();

    static {
        registerBlock("air", Blocks.AIR);
        registerBlock("stone", Blocks.STONE);
    }

    private Registries() {
    }

    public static Block registerBlock(String name, Block block) {
        Identifier id = new Identifier("minecraft", name);
        ((SimpleRegistry<Block>) BLOCK).register(id, block);
        return block;
    }

    private static class SimpleRegistry<T> implements Registry<T> {
        private final Map<Identifier, T> byId = new HashMap<>();
        private final Map<T, Identifier> byEntry = new HashMap<>();

        public void register(Identifier id, T entry) {
            byId.put(id, entry);
            byEntry.put(entry, id);
        }

        @Override
        public T get(Identifier id) {
            return byId.get(id);
        }

        @Override
        public Identifier getId(T entry) {
            return byEntry.get(entry);
        }

        @Override
        public boolean containsId(Identifier id) {
            return byId.containsKey(id);
        }

        @Override
        public Optional<T> getOrEmpty(Identifier id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Set<Identifier> getIds() {
            return byId.keySet();
        }
    }
}
