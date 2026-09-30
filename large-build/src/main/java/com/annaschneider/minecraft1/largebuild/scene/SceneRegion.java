package com.annaschneider.minecraft1.largebuild.scene;

import com.annaschneider.minecraft1.domain.MirrorAxis;

import java.util.Objects;

/**
 * One high-level element of a scene. {@code x,y,z} is where the generator's local origin is placed (scene coordinates,
 * y=0 is the ground/island-top level). Size semantics:
 * <ul>
 *   <li>PALACE_CORE: sizeX = footprint width, sizeY = height</li>
 *   <li>BRIDGE / PATH: sizeX = length along local +X, sizeZ = width, sizeY = arch depth (bridge only)</li>
 *   <li>TERRACE: sizeX = diameter, sizeY = total height</li>
 *   <li>ISLAND: sizeX = diameter, sizeY = depth below the surface</li>
 *   <li>WATERFALL: sizeX = width, sizeY = fall height</li>
 *   <li>GARDEN / CHERRY_TREES: sizeX = diameter</li>
 *   <li>CLOUDS: sizeX x sizeZ = area starting at the origin, sizeY = thickness</li>
 * </ul>
 */
public record SceneRegion(
    String id,
    RegionType type,
    int x,
    int y,
    int z,
    int sizeX,
    int sizeY,
    int sizeZ,
    int rotation,
    MirrorAxis mirror,
    long seed
) {
    public static final int MAX_HORIZONTAL_SIZE = 16_384;
    public static final int MAX_VERTICAL_SIZE = 384;
    public static final int MAX_OFFSET = 1_000_000;

    public SceneRegion {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new IllegalArgumentException("Region id must be 1..64 characters.");
        }
        Objects.requireNonNull(type, "Region type is required (region " + id + ").");
        if (sizeX < 1 || sizeZ < 1 || sizeX > MAX_HORIZONTAL_SIZE || sizeZ > MAX_HORIZONTAL_SIZE) {
            throw new IllegalArgumentException("Region " + id + " horizontal size must be 1.." + MAX_HORIZONTAL_SIZE + ".");
        }
        if (sizeY < 1 || sizeY > MAX_VERTICAL_SIZE) {
            throw new IllegalArgumentException("Region " + id + " vertical size must be 1.." + MAX_VERTICAL_SIZE + ".");
        }
        if (Math.abs(x) > MAX_OFFSET || Math.abs(z) > MAX_OFFSET || Math.abs(y) > MAX_VERTICAL_SIZE) {
            throw new IllegalArgumentException("Region " + id + " position is out of range.");
        }
        if (Math.floorMod(rotation, 90) != 0) {
            throw new IllegalArgumentException("Region " + id + " rotation must be a multiple of 90.");
        }
        rotation = Math.floorMod(rotation, 360);
        mirror = mirror == null ? MirrorAxis.NONE : mirror;
    }
}
