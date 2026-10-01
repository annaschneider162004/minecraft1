package net.minecraft.world.border;

import net.minecraft.util.math.BlockPos;

public class WorldBorder {
    private double size = 60_000_000.0;
    private double centerX = 0.0;
    private double centerZ = 0.0;

    public boolean contains(BlockPos pos) {
        double half = size / 2.0;
        return pos.getX() >= centerX - half && pos.getX() <= centerX + half
            && pos.getZ() >= centerZ - half && pos.getZ() <= centerZ + half;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
    }
}
