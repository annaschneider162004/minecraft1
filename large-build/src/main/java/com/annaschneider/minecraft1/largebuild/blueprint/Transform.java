package com.annaschneider.minecraft1.largebuild.blueprint;

import com.annaschneider.minecraft1.domain.MirrorAxis;

/**
 * Mirror followed by a clockwise rotation around Y (same convention as {@code Blueprint.rotated}: 90 degrees maps
 * +X to +Z).
 */
public record Transform(int rotation, MirrorAxis mirror) {
    public static final Transform IDENTITY = new Transform(0, MirrorAxis.NONE);

    public Transform {
        if (Math.floorMod(rotation, 90) != 0) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees.");
        }
        rotation = Math.floorMod(rotation, 360);
        mirror = mirror == null ? MirrorAxis.NONE : mirror;
    }

    public static Transform of(int rotation, MirrorAxis mirror) {
        return new Transform(rotation, mirror);
    }

    public int applyX(int x, int z) {
        int mx = mirror == MirrorAxis.X ? -x : x;
        int mz = mirror == MirrorAxis.Z ? -z : z;
        return switch (rotation) {
            case 90 -> -mz;
            case 180 -> -mx;
            case 270 -> mz;
            default -> mx;
        };
    }

    public int applyZ(int x, int z) {
        int mx = mirror == MirrorAxis.X ? -x : x;
        int mz = mirror == MirrorAxis.Z ? -z : z;
        return switch (rotation) {
            case 90 -> mx;
            case 180 -> -mz;
            case 270 -> -mx;
            default -> mz;
        };
    }

    public int inverseX(int x, int z) {
        int rx = switch (rotation) {
            case 90 -> z;
            case 180 -> -x;
            case 270 -> -z;
            default -> x;
        };
        return mirror == MirrorAxis.X ? -rx : rx;
    }

    public int inverseZ(int x, int z) {
        int rz = switch (rotation) {
            case 90 -> -x;
            case 180 -> -z;
            case 270 -> x;
            default -> z;
        };
        return mirror == MirrorAxis.Z ? -rz : rz;
    }

    public Bounds apply(Bounds local) {
        int ax = applyX(local.minX(), local.minZ());
        int az = applyZ(local.minX(), local.minZ());
        int bx = applyX(local.maxX(), local.maxZ());
        int bz = applyZ(local.maxX(), local.maxZ());
        return new Bounds(Math.min(ax, bx), local.minY(), Math.min(az, bz), Math.max(ax, bx), local.maxY(), Math.max(az, bz));
    }
}
