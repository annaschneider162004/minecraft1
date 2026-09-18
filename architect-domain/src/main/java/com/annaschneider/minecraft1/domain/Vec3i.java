package com.annaschneider.minecraft1.domain;

public record Vec3i(int x, int y, int z) {
    public Vec3i add(Vec3i other) {
        return new Vec3i(x + other.x, y + other.y, z + other.z);
    }

    public Vec3i offset(int dx, int dy, int dz) {
        return new Vec3i(x + dx, y + dy, z + dz);
    }
}
