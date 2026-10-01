package net.minecraft.util.math;

import java.util.Objects;

public class ChunkPos {
    public final int x;
    public final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public ChunkPos(BlockPos pos) {
        this.x = pos.getX() >> 4;
        this.z = pos.getZ() >> 4;
    }

    public static long toLong(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public long toLong() {
        return toLong(this.x, this.z);
    }

    public int getStartX() {
        return this.x << 4;
    }

    public int getStartZ() {
        return this.z << 4;
    }

    public int getEndX() {
        return (this.x << 4) + 15;
    }

    public int getEndZ() {
        return (this.z << 4) + 15;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos chunkPos)) return false;
        return x == chunkPos.x && z == chunkPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "[" + x + ", " + z + "]";
    }
}
