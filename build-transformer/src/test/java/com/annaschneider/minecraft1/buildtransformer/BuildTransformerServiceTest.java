package com.annaschneider.minecraft1.buildtransformer;

import com.annaschneider.minecraft1.domain.Blueprint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildTransformerServiceTest {
    private final BuildTransformerService service = new BuildTransformerService();

    @Test
    void upgradeTransformsExpectedMaterials() {
        Blueprint source = Blueprint.builder("house")
            .add(0, 0, 0, "oak_planks")
            .add(1, 0, 0, "cobblestone")
            .build();

        Blueprint transformed = service.transform(TransformOperation.UPGRADE, "house", source);

        assertEquals("minecraft:stone_bricks", transformed.blocks().get(0).blockId());
        assertEquals("minecraft:polished_andesite", transformed.blocks().get(1).blockId());
    }

    @Test
    void damageModeIsDeterministic() {
        Blueprint source = Blueprint.builder("test")
            .add(3, 0, 4, "stone_bricks")
            .add(4, 0, 4, "stone_bricks")
            .build();

        Blueprint first = service.transform(TransformOperation.DAMAGE, "house", source);
        Blueprint second = service.transform(TransformOperation.DAMAGE, "house", source);

        assertEquals(first.blocks(), second.blocks());
    }
}
