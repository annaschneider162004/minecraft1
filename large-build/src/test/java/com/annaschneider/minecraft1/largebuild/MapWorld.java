package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.largebuild.engine.WorldAccess;

import java.util.HashMap;
import java.util.Map;

/** Sparse in-memory world for tests; unset positions are air. */
public class MapWorld implements WorldAccess {
    public static final String AIR = "minecraft:air";
    public final Map<Long, String> blocks = new HashMap<>();
    public long writes;

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @Override
    public String getBlock(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), AIR);
    }

    @Override
    public void setBlock(int x, int y, int z, String blockId) {
        writes++;
        if (AIR.equals(blockId)) {
            blocks.remove(key(x, y, z));
        } else {
            blocks.put(key(x, y, z), blockId);
        }
    }
}
