package com.annaschneider.minecraft1.worldgen;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBuilder;

public final class FantasyKingdomGenerator {
    public Blueprint generateKingdom() {
        BlueprintBuilder b = Blueprint.builder("world-kingdom");

        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                b.add(x, 0, z, "grass_block");
            }
        }

        for (int x = -24; x <= 24; x++) {
            b.add(x, 1, 0, "dirt_path");
        }
        for (int z = -24; z <= 24; z++) {
            b.add(0, 1, z, "dirt_path");
        }

        for (int x = -20; x <= -10; x++) {
            for (int z = 8; z <= 18; z++) {
                b.add(x, 1, z, "farmland");
            }
        }

        for (int x = 10; x <= 20; x += 2) {
            for (int z = -18; z <= -8; z += 2) {
                b.add(x, 1, z, "oak_log");
                b.add(x, 2, z, "oak_log");
                b.add(x, 3, z, "oak_log");
                b.add(x, 4, z, "oak_leaves");
            }
        }

        for (int z = -24; z <= 24; z++) {
            b.add(-8, 1, z, "water");
            b.add(-9, 1, z, "water");
        }

        b.add(0, 1, 0, "blue_wool");
        b.add(-15, 1, 12, "yellow_wool");
        b.add(15, 1, -12, "green_wool");
        b.add(-8, 1, 20, "cyan_wool");

        return b.build();
    }
}
