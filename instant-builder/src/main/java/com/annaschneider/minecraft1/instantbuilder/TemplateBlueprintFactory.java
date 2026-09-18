package com.annaschneider.minecraft1.instantbuilder;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBuilder;

public final class TemplateBlueprintFactory {
    public Blueprint house() {
        BlueprintBuilder b = Blueprint.builder("house");
        for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) b.add(x, 0, z, "stone_bricks");
        for (int y = 1; y <= 4; y++) for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) {
            if (x == 0 || x == 8 || z == 0 || z == 6) {
                b.add(x, y, z, "oak_planks");
            }
        }
        for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) b.add(x, 5, z, "spruce_planks");
        for (int y = 1; y <= 2; y++) b.add(4, y, 0, "air");
        return b.build();
    }

    public Blueprint castle() {
        BlueprintBuilder b = Blueprint.builder("castle");
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) if (Math.abs(x) == 8 || Math.abs(z) == 8) for (int y = 0; y < 5; y++) b.add(x, y, z, "stone_bricks");
        for (int[] c : new int[][]{{-8, -8}, {-8, 8}, {8, -8}, {8, 8}}) {
            for (int x = c[0] - 1; x <= c[0] + 1; x++) for (int z = c[1] - 1; z <= c[1] + 1; z++) for (int y = 0; y < 9; y++) b.add(x, y, z, "cobblestone");
        }
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) b.add(x, 0, z, "stone_bricks");
        return b.build();
    }

    public Blueprint temple() {
        BlueprintBuilder b = Blueprint.builder("temple");
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) b.add(x, 0, z, "cut_sandstone");
        for (int y = 1; y <= 5; y++) {
            for (int x = -6; x <= 6; x++) {
                b.add(x, y, -6, "smooth_sandstone");
                b.add(x, y, 6, "smooth_sandstone");
            }
            for (int z = -6; z <= 6; z++) {
                b.add(-6, y, z, "smooth_sandstone");
                b.add(6, y, z, "smooth_sandstone");
            }
        }
        for (int y = 1; y <= 5; y++) for (int[] p : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) b.add(p[0], y, p[1], "chiseled_sandstone");
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) b.add(x, 6, z, "sandstone_slab");
        return b.build();
    }

    public Blueprint village() {
        BlueprintBuilder b = Blueprint.builder("village");
        Blueprint house = house();
        for (var p : house.blocks()) {
            b.add(p.position().x() - 8, p.position().y(), p.position().z() - 6, p.blockId());
            b.add(p.position().x() + 8, p.position().y(), p.position().z() + 6, p.blockId());
        }
        for (int x = -16; x <= 16; x++) for (int z = -1; z <= 1; z++) b.add(x, 0, z, "dirt_path");
        return b.build();
    }
}
