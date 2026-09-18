package com.annaschneider.minecraft1.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlueprintTransformTest {
    @Test
    void rotatesBlueprint90DegreesAroundY() {
        Blueprint source = Blueprint.builder("shape")
            .add(2, 1, 0, "stone")
            .build();

        Blueprint rotated = source.rotated(90);

        assertEquals(new Vec3i(0, 1, 2), rotated.blocks().get(0).position());
    }

    @Test
    void mirrorsBlueprintOnXAxis() {
        Blueprint source = Blueprint.builder("shape")
            .add(3, 0, -2, "stone")
            .build();

        Blueprint mirrored = source.mirrored(MirrorAxis.X);

        assertEquals(new Vec3i(-3, 0, -2), mirrored.blocks().get(0).position());
    }

    @Test
    void validatesMaxBlockLimit() {
        Blueprint source = Blueprint.builder("shape")
            .add(0, 0, 0, "stone")
            .add(1, 0, 0, "stone")
            .build();

        assertThrows(IllegalArgumentException.class, () -> BlueprintValidation.validateBlueprint(source, 1, 64));
    }
}
