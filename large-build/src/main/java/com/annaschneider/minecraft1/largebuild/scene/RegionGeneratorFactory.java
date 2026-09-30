package com.annaschneider.minecraft1.largebuild.scene;

import com.annaschneider.minecraft1.largebuild.generator.BridgeGenerator;
import com.annaschneider.minecraft1.largebuild.generator.CherryGroveGenerator;
import com.annaschneider.minecraft1.largebuild.generator.CloudLayerGenerator;
import com.annaschneider.minecraft1.largebuild.generator.GardenGenerator;
import com.annaschneider.minecraft1.largebuild.generator.IslandGenerator;
import com.annaschneider.minecraft1.largebuild.generator.PalaceCoreGenerator;
import com.annaschneider.minecraft1.largebuild.generator.PathGenerator;
import com.annaschneider.minecraft1.largebuild.generator.StructureGenerator;
import com.annaschneider.minecraft1.largebuild.generator.TerraceGenerator;
import com.annaschneider.minecraft1.largebuild.generator.WaterfallGenerator;

/**
 * Maps scene regions to deterministic generators.
 */
public final class RegionGeneratorFactory {
    public StructureGenerator create(SceneRegion region) {
        try {
            return switch (region.type()) {
                case PALACE_CORE -> new PalaceCoreGenerator(region.sizeX() / 2, region.sizeY());
                case BRIDGE -> new BridgeGenerator(region.sizeX(), Math.max(1, (region.sizeZ() - 3) / 2), region.sizeY());
                case TERRACE -> terrace(region);
                case ISLAND -> new IslandGenerator(region.sizeX() / 2, region.sizeY(), region.seed());
                case WATERFALL -> new WaterfallGenerator(region.sizeX(), region.sizeY(), region.seed());
                case PATH -> new PathGenerator(region.sizeX(), region.sizeZ() / 2, region.seed());
                case GARDEN -> new GardenGenerator(region.sizeX() / 2, region.seed());
                case CHERRY_TREES -> new CherryGroveGenerator(region.sizeX() / 2, region.seed());
                case CLOUDS -> new CloudLayerGenerator(region.sizeX(), region.sizeZ(), Math.min(8, region.sizeY()), 22, region.seed());
            };
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Region '" + region.id() + "' (" + region.type().id() + "): " + ex.getMessage(), ex);
        }
    }

    private static TerraceGenerator terrace(SceneRegion region) {
        int radius = region.sizeX() / 2;
        int levels = Math.max(1, Math.min(8, region.sizeY() / 4));
        int step = Math.max(2, Math.min(12, region.sizeY() / levels));
        int ring = Math.max(2, Math.min(64, (radius - 4) / (levels + 1)));
        return new TerraceGenerator(radius, levels, step, ring);
    }
}
