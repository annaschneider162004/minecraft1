package com.annaschneider.minecraft1.buildassistant;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBuilder;

public final class ShapeBuilderService {
    public Blueprint wall(int length, int height, String blockId) {
        validatePositive(length, "length");
        validatePositive(height, "height");
        BlueprintBuilder builder = Blueprint.builder("shape-wall");
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                builder.add(x, y, 0, blockId);
            }
        }
        return builder.build();
    }

    public Blueprint column(int height, String blockId) {
        validatePositive(height, "height");
        BlueprintBuilder builder = Blueprint.builder("shape-column");
        for (int y = 0; y < height; y++) {
            builder.add(0, y, 0, blockId);
        }
        return builder.build();
    }

    public Blueprint sphere(int radius, String blockId) {
        validatePositive(radius, "radius");
        BlueprintBuilder builder = Blueprint.builder("shape-sphere");
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 <= r2) {
                        builder.add(x, y, z, blockId);
                    }
                }
            }
        }
        return builder.build();
    }

    private static void validatePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
    }
}
