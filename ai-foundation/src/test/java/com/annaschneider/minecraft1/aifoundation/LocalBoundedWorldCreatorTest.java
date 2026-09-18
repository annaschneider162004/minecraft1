package com.annaschneider.minecraft1.aifoundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalBoundedWorldCreatorTest {
    @Test
    void maxRadiusChangesGeneratedBlueprintSize() {
        LocalBoundedWorldCreator creator = new LocalBoundedWorldCreator();

        int small = creator.createPlan(new WorldCreationRequest("kingdom", 16)).previewBlueprint().blocks().size();
        int large = creator.createPlan(new WorldCreationRequest("kingdom", 64)).previewBlueprint().blocks().size();

        assertTrue(large > small);
    }
}
