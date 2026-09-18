package com.annaschneider.minecraft1.domain;

import java.util.ArrayList;
import java.util.List;

public final class Blueprint {
    private final String name;
    private final List<BlueprintBlock> blocks;

    private Blueprint(String name, List<BlueprintBlock> blocks) {
        this.name = BlueprintValidation.requireName(name);
        this.blocks = BlueprintValidation.requireBlocks(blocks);
    }

    public static Blueprint of(String name, List<BlueprintBlock> blocks) {
        return new Blueprint(name, blocks);
    }

    public static BlueprintBuilder builder(String name) {
        return new BlueprintBuilder(name);
    }

    public String name() {
        return name;
    }

    public List<BlueprintBlock> blocks() {
        return blocks;
    }

    public Blueprint rotated(int degrees) {
        int turns = Math.floorMod(degrees, 360) / 90;
        List<BlueprintBlock> rotated = new ArrayList<>(blocks.size());
        for (BlueprintBlock block : blocks) {
            Vec3i p = block.position();
            int x = p.x();
            int z = p.z();
            for (int i = 0; i < turns; i++) {
                int nx = -z;
                z = x;
                x = nx;
            }
            rotated.add(new BlueprintBlock(new Vec3i(x, p.y(), z), block.blockId()));
        }
        return new Blueprint(name + "-rot" + Math.floorMod(degrees, 360), rotated);
    }

    public Blueprint mirrored(MirrorAxis axis) {
        if (axis == null || axis == MirrorAxis.NONE) {
            return this;
        }
        List<BlueprintBlock> mirrored = new ArrayList<>(blocks.size());
        for (BlueprintBlock block : blocks) {
            Vec3i p = block.position();
            Vec3i next = switch (axis) {
                case X -> new Vec3i(-p.x(), p.y(), p.z());
                case Z -> new Vec3i(p.x(), p.y(), -p.z());
                default -> p;
            };
            mirrored.add(new BlueprintBlock(next, block.blockId()));
        }
        return new Blueprint(name + "-mir-" + axis.name().toLowerCase(), mirrored);
    }

    public Blueprint translated(int dx, int dy, int dz) {
        List<BlueprintBlock> translated = new ArrayList<>(blocks.size());
        for (BlueprintBlock block : blocks) {
            translated.add(new BlueprintBlock(block.position().offset(dx, dy, dz), block.blockId()));
        }
        return new Blueprint(name + "-offset", translated);
    }

    public Blueprint renamed(String newName) {
        return new Blueprint(newName, blocks);
    }
}
