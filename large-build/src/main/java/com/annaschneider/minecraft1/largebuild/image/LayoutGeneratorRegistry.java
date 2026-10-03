package com.annaschneider.minecraft1.largebuild.image;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Sampling order is part of the reproducibility contract; never depend on HashMap iteration. */
public final class LayoutGeneratorRegistry {
    public static final List<LayoutType> ORDER = List.of(LayoutType.RADIAL, LayoutType.LINEAR,
        LayoutType.TERRACED, LayoutType.RING, LayoutType.GRID, LayoutType.CLIFF);
    private final Map<LayoutType, LayoutGenerator> generators = new EnumMap<>(LayoutType.class);

    public LayoutGeneratorRegistry() {
        for (LayoutType type : ORDER) generators.put(type, new GroundedLayoutGenerator(type));
    }

    public List<LayoutGenerator> generators() {
        return ORDER.stream().map(generators::get).toList();
    }

    public LayoutGenerator get(LayoutType layout) {
        LayoutGenerator generator = generators.get(layout);
        if (generator == null) throw new IllegalArgumentException("No structural generator for " + layout + ".");
        return generator;
    }
}
