package com.annaschneider.minecraft1.buildassistant;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.MirrorAxis;

public final class StructureOperationService {
    public Blueprint copy(Blueprint blueprint) {
        return blueprint.renamed(blueprint.name() + "-copy");
    }

    public Blueprint rotate(Blueprint blueprint, int degrees) {
        return blueprint.rotated(degrees);
    }

    public Blueprint mirror(Blueprint blueprint, MirrorAxis axis) {
        return blueprint.mirrored(axis);
    }

    public Blueprint move(Blueprint blueprint, int dx, int dy, int dz) {
        return blueprint.translated(dx, dy, dz);
    }
}
