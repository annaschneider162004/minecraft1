package com.annaschneider.minecraft1.link;

/** Top-down footprint of one plan region, relative to the build origin. */
public record RegionBox(String type, int minX, int minZ, int maxX, int maxZ) {
}
