package com.annaschneider.minecraft1.largebuild.generator;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.Set;

/**
 * Arched stone bridge running along local +X from x=0 with the deck at y=0.
 */
public final class BridgeGenerator implements StructureGenerator {
    private static final String DECK = "minecraft:stone_bricks";
    private static final String CENTER = "minecraft:smooth_quartz";
    private static final String CURB = "minecraft:quartz_block";
    private static final String POST = "minecraft:quartz_pillar";
    private static final String RAIL = "minecraft:quartz_slab";
    private static final String LIGHT = "minecraft:lantern";

    private final int length;
    private final int halfWidth;
    private final int archDepth;
    private final Bounds bounds;

    public BridgeGenerator(int length, int halfWidth, int archDepth) {
        this.length = Noise.requireRange(length, 4, 4096, "bridge length");
        this.halfWidth = Noise.requireRange(halfWidth, 1, 8, "bridge halfWidth");
        this.archDepth = Noise.requireRange(archDepth, 1, 48, "bridge archDepth");
        this.bounds = new Bounds(0, -archDepth, -(halfWidth + 1), length - 1, 4, halfWidth + 1);
    }

    @Override
    public String id() {
        return "bridge";
    }

    @Override
    public Bounds localBounds() {
        return bounds;
    }

    @Override
    public Set<String> palette() {
        return Set.of(DECK, CENTER, CURB, POST, RAIL, LIGHT);
    }

    @Override
    public String blockAt(int x, int y, int z) {
        int az = Math.abs(z);
        if (az <= halfWidth) {
            if (y == 0) {
                return az == 0 ? CENTER : DECK;
            }
            if (y < 0) {
                double t = length == 1 ? 0 : Math.sin(Math.PI * x / (length - 1));
                int depth = 1 + (int) Math.round((archDepth - 1) * (1 - t));
                return -y <= depth ? DECK : null;
            }
            return null;
        }
        boolean post = Math.floorMod(x, 8) == 0 || x == length - 1;
        if (y == 0) {
            return CURB;
        }
        if (post) {
            return y <= 3 ? POST : y == 4 ? LIGHT : null;
        }
        return y == 1 ? RAIL : null;
    }
}
