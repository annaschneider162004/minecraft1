package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.RegionType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline, deterministic "analysis" using only metadata: the content hash seeds the layout, the aspect ratio shapes it,
 * and file-name keywords select region types. It does not look at pixels.
 */
public final class HeuristicImageAnalysisProvider implements ImageAnalysisProvider {
    public static final String ID = "heuristic-metadata-v1";

    private static final Map<String, RegionType> KEYWORDS = new LinkedHashMap<>();

    static {
        for (String k : List.of("palace", "castle", "temple", "tower", "kingdom", "fortress")) {
            KEYWORDS.put(k, RegionType.PALACE_CORE);
        }
        KEYWORDS.put("bridge", RegionType.BRIDGE);
        KEYWORDS.put("bridges", RegionType.BRIDGE);
        KEYWORDS.put("terrace", RegionType.TERRACE);
        KEYWORDS.put("terraces", RegionType.TERRACE);
        KEYWORDS.put("waterfall", RegionType.WATERFALL);
        KEYWORDS.put("waterfalls", RegionType.WATERFALL);
        KEYWORDS.put("falls", RegionType.WATERFALL);
        KEYWORDS.put("island", RegionType.ISLAND);
        KEYWORDS.put("islands", RegionType.ISLAND);
        KEYWORDS.put("floating", RegionType.ISLAND);
        KEYWORDS.put("sky", RegionType.CLOUDS);
        KEYWORDS.put("cloud", RegionType.CLOUDS);
        KEYWORDS.put("clouds", RegionType.CLOUDS);
        KEYWORDS.put("garden", RegionType.GARDEN);
        KEYWORDS.put("gardens", RegionType.GARDEN);
        KEYWORDS.put("flower", RegionType.GARDEN);
        KEYWORDS.put("cherry", RegionType.CHERRY_TREES);
        KEYWORDS.put("sakura", RegionType.CHERRY_TREES);
        KEYWORDS.put("blossom", RegionType.CHERRY_TREES);
        KEYWORDS.put("path", RegionType.PATH);
        KEYWORDS.put("road", RegionType.PATH);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ImageAnalysis analyze(ImageReference image) {
        long seed = Long.parseUnsignedLong(image.sha256().substring(0, 16), 16);
        Set<RegionType> matched = EnumSet.noneOf(RegionType.class);
        List<String> tokens = List.of(image.name().toLowerCase(Locale.ROOT).split("[^a-z0-9]+"));
        for (String token : tokens) {
            RegionType type = KEYWORDS.get(token);
            if (type != null) {
                matched.add(type);
            }
        }
        List<String> notes = new ArrayList<>();
        notes.add("Heuristic analysis: pixels are not inspected; layout is seeded by the content hash.");
        Set<RegionType> features;
        if (matched.isEmpty()) {
            features = EnumSet.allOf(RegionType.class);
            notes.add("No keywords in the image name; using the full fantasy scene (palace, terraces, islands, bridges, waterfalls, gardens, cherry trees, paths, clouds).");
        } else {
            features = EnumSet.copyOf(matched);
            features.add(RegionType.ISLAND);
            features.add(RegionType.PALACE_CORE);
            features.add(RegionType.BRIDGE);
            notes.add("Keywords matched: " + matched + " (islands, palace core and bridges are always included as the scene skeleton).");
        }
        String style = tokens.contains("dark") || tokens.contains("night") ? "dark_fantasy"
            : tokens.contains("desert") || tokens.contains("sand") ? "desert" : "white_gold";
        if (image.width() > 0 && image.height() > 0) {
            notes.add("Image size " + image.width() + "x" + image.height() + " shapes the scene aspect ratio.");
        }
        return new ImageAnalysis(image, ID, seed, image.width(), image.height(), features, style, 0.2, notes);
    }
}
