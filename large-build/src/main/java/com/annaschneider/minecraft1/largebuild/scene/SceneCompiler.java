package com.annaschneider.minecraft1.largebuild.scene;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.PlacedStructure;
import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.blueprint.Transform;

import java.util.ArrayList;
import java.util.List;
import com.annaschneider.minecraft1.largebuild.image.StylePreset;

/**
 * Converts a {@link ScenePlan} into a lazily evaluated {@link ProceduralBlueprint}. Regions are layered in plan order
 * (later regions override earlier ones where they overlap).
 */
public final class SceneCompiler {
    private final RegionGeneratorFactory factory;

    public SceneCompiler() {
        this(new RegionGeneratorFactory());
    }

    public SceneCompiler(RegionGeneratorFactory factory) {
        this.factory = factory;
    }

    public ProceduralBlueprint compile(ScenePlan plan, long maxSections) {
        List<PlacedStructure> placed = new ArrayList<>(plan.regions().size());
        for (SceneRegion region : plan.regions()) {
            if (plan.metadata() == null) {
                placed.add(place(region));
            } else {
                placed.add(new PlacedStructure(factory.create(region, StylePreset.fromId(plan.metadata().style())),
                    new Vec3i(region.x(), region.y(), region.z()), Transform.of(region.rotation(), region.mirror())));
            }
        }
        return new ProceduralBlueprint("scene-" + plan.id(), placed, maxSections);
    }

    public PlacedStructure place(SceneRegion region) {
        return new PlacedStructure(factory.create(region), new Vec3i(region.x(), region.y(), region.z()),
            Transform.of(region.rotation(), region.mirror()));
    }
}
