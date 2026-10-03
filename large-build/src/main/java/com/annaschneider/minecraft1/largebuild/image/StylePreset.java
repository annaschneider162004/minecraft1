package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.RegionType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Small offline presets: both building vocabulary and block materials are style-dependent. */
public enum StylePreset {
    FANTASY("minecraft:quartz_block", "minecraft:dark_oak_planks", "minecraft:purple_concrete",
        "minecraft:stone_bricks", Set.of("building", "tower", "garden", "trees", "wall", "water", "waterfall")),
    MEDIEVAL("minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:dark_oak_planks",
        "minecraft:cobblestone", Set.of("building", "tower", "garden", "trees", "wall", "water", "waterfall")),
    DESERT("minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:red_sandstone",
        "minecraft:smooth_sandstone", Set.of("building", "tower", "garden", "wall", "water", "waterfall")),
    INDUSTRIAL("minecraft:bricks", "minecraft:iron_block", "minecraft:gray_concrete",
        "minecraft:polished_andesite", Set.of("building", "wall", "water", "waterfall")),
    NEUTRAL("minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:stone_slab",
        "minecraft:gravel", Set.of("building", "tower", "garden", "trees", "wall", "water", "waterfall"));

    private final String masonry;
    private final String timber;
    private final String roof;
    private final String road;
    private final Set<String> supported;

    StylePreset(String masonry, String timber, String roof, String road, Set<String> supported) {
        this.masonry = masonry;
        this.timber = timber;
        this.roof = roof;
        this.road = road;
        this.supported = supported;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public Set<String> supportedFeatures() { return supported; }
    public String masonry() { return masonry; }
    public String timber() { return timber; }
    public String roof() { return roof; }
    public String road() { return road; }
    public String ground() { return this == DESERT ? "minecraft:sandstone" : "minecraft:stone"; }

    public static StylePreset fromId(String id) {
        if (id == null || id.isBlank()) return NEUTRAL;
        String normalized = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("white_gold") || normalized.equals("dark_fantasy")) return FANTASY;
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown style '" + id + "'. Expected fantasy, medieval, desert, industrial or neutral.");
        }
    }

    public Map<LayoutType, Double> weights() {
        EnumMap<LayoutType, Double> result = new EnumMap<>(LayoutType.class);
        for (LayoutType layout : LayoutGeneratorRegistry.ORDER) result.put(layout, 1.0);
        switch (this) {
            case FANTASY -> { result.put(LayoutType.RADIAL, 4.0); result.put(LayoutType.CLIFF, 2.0); }
            case MEDIEVAL -> { result.put(LayoutType.RING, 4.0); result.put(LayoutType.LINEAR, 2.0); }
            case DESERT -> { result.put(LayoutType.TERRACED, 4.0); result.put(LayoutType.RING, 2.0); }
            case INDUSTRIAL -> { result.put(LayoutType.GRID, 5.0); result.put(LayoutType.LINEAR, 3.0); result.put(LayoutType.CLIFF, 0.0); }
            default -> { }
        }
        return result;
    }

    public boolean supports(RegionType type) {
        return type != RegionType.TOWER || supported.contains("tower");
    }
}
