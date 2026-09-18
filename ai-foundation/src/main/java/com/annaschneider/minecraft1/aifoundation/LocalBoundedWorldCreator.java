package com.annaschneider.minecraft1.aifoundation;

import com.annaschneider.minecraft1.worldgen.FantasyKingdomGenerator;

import java.util.List;

public final class LocalBoundedWorldCreator implements AIWorldCreatorOrchestrator {
    private final FantasyKingdomGenerator generator = new FantasyKingdomGenerator();

    @Override
    public WorldPlan createPlan(WorldCreationRequest request) {
        return new WorldPlan(
            List.of(
                "Create bounded terrain platform (deterministic).",
                "Lay road cross centered at origin.",
                "Place farm, forest, and river zones.",
                "Mark districts for future AI expansion."
            ),
            generator.generateKingdom(),
            true
        );
    }
}
