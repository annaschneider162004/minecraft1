package com.annaschneider.minecraft1.largebuild.image;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** A bounded vocabulary parser, not an LLM. Every clause is inspected, including negative constraints. */
public final class OfflinePromptParser {
    public static final int MAX_PROMPT_LENGTH = 4096;
    private static final Pattern NEGATION = Pattern.compile(
        "\\b(?:without|no|not|exclude|excluding|avoid)\\s+([^,;.]*?)(?=\\b(?:but|with|including|include)\\b|[,;.]|$)");
    private static final Map<String, String> FEATURES = aliases();

    public PromptAnalysis parse(String prompt) {
        String original = prompt == null ? "" : prompt;
        if (original.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException("Prompt exceeds " + MAX_PROMPT_LENGTH + " characters.");
        }
        String text = original.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        List<String> notes = new ArrayList<>();
        Set<String> excluded = new LinkedHashSet<>();
        var negatives = NEGATION.matcher(text);
        StringBuilder positive = new StringBuilder(text);
        while (negatives.find()) {
            String clause = negatives.group(1);
            collect(clause, excluded);
            String remainder = clause;
            for (String alias : FEATURES.keySet()) {
                remainder = remainder.replaceAll("\\b" + Pattern.quote(alias) + "\\b", " ");
            }
            for (LayoutType layout : LayoutGeneratorRegistry.ORDER) {
                if (word(clause, layout.name().toLowerCase(Locale.ROOT))) excluded.add(layout.name().toLowerCase(Locale.ROOT));
                remainder = remainder.replaceAll("\\b" + layout.name().toLowerCase(Locale.ROOT) + "\\b", " ");
            }
            remainder = remainder.replaceAll("\\b(?:and|or|a|an|the|any|central|no|not)\\b", " ")
                .replaceAll("[^a-z]+", " ").strip();
            if (!remainder.isEmpty()) notes.add("Unknown exclusion constraint: " + remainder);
            for (int i = negatives.start(); i < negatives.end(); i++) positive.setCharAt(i, ' ');
        }
        String requested = positive.toString();
        Set<String> features = new LinkedHashSet<>();
        collect(requested, features);
        features.removeAll(excluded);
        StylePreset style = StylePreset.NEUTRAL;
        for (StylePreset preset : StylePreset.values()) {
            if (word(requested, preset.id())) { style = preset; break; }
        }
        var styleField = Pattern.compile("\\bstyle\\s*[:=]\\s*([a-z]+)").matcher(requested);
        if (styleField.find()) style = StylePreset.fromId(styleField.group(1));
        LayoutType layout = LayoutType.AUTO;
        for (LayoutType candidate : LayoutType.values()) {
            if (candidate != LayoutType.AUTO && word(requested, candidate.name().toLowerCase(Locale.ROOT))) {
                layout = candidate;
                break;
            }
        }
        var layoutField = Pattern.compile("\\blayout\\s*[:=]\\s*([a-z]+)").matcher(requested);
        if (layoutField.find()) layout = LayoutType.fromId(layoutField.group(1));
        String subject = "settlement";
        for (String candidate : List.of("village", "town", "city", "castle", "fortress", "factory", "oasis", "harbor")) {
            if (word(requested, candidate)) { subject = candidate; break; }
        }
        if (subject.equals("factory") && style == StylePreset.NEUTRAL) style = StylePreset.INDUSTRIAL;
        String terrain = "flat";
        if (word(requested, "cliff") || word(requested, "cliffside")) terrain = "cliff";
        else if (word(requested, "mountain") || word(requested, "mountainous") || word(requested, "hillside")) terrain = "hillside";
        else if (word(requested, "river") || word(requested, "riverside")) terrain = "river";
        else if (word(requested, "coast") || word(requested, "coastal")) terrain = "coast";
        else if (word(requested, "desert")) terrain = "desert";
        for (String unknown : List.of("steampunk", "gothic", "sci fi", "cyberpunk", "victorian", "futuristic",
            "baroque", "brutalist", "japanese", "norse", "dwarven", "volcanic basalt")) {
            if (word(requested, unknown)) notes.add("Unknown style/material hint '" + unknown + "'; not applied.");
        }
        for (String constraint : List.of("rotating gears", "symmetrical", "facing sunrise", "facing north", "facing south")) {
            if (word(requested, constraint)) notes.add("Unsupported constraint '" + constraint + "'; not applied.");
        }
        for (String unknown : List.of("volcanic", "underground", "swamp", "ocean", "tundra", "lava", "cave")) {
            if (word(requested, unknown)) notes.add("Unsupported terrain hint '" + unknown + "'; not used for layout filtering.");
        }
        if (terrain.equals("river") || terrain.equals("coast")) {
            notes.add("Terrain hint '" + terrain + "' recorded; rivers and shorelines are not modeled by these generators.");
        }
        String remainder = requested;
        for (String alias : FEATURES.keySet()) remainder = remainder.replaceAll("\\b" + Pattern.quote(alias) + "\\b", " ");
        for (StylePreset preset : StylePreset.values()) remainder = remainder.replaceAll("\\b" + preset.id() + "\\b", " ");
        for (LayoutType candidate : LayoutType.values()) remainder = remainder.replaceAll("\\b" + candidate.name().toLowerCase(Locale.ROOT) + "\\b", " ");
        remainder = remainder.replaceAll("\\b(?:settlement|village|town|city|castle|fortress|factory|oasis|harbor|"
            + "flat|cliffside|cliff|mountain|mountainous|hillside|river|riverside|coast|coastal|desert|"
            + "a|an|the|and|or|but|with|without|using|use|of|on|in|at|to|for|from|"
            + "build|create|generate|make|please|style|layout|include|including|central|no|not)\\b", " ")
            .replaceAll("[^a-z]+", " ").strip();
        if (!remainder.isEmpty()) {
            notes.add("Uninterpreted prompt vocabulary/constraints (bounded offline parser): "
                + remainder.substring(0, Math.min(256, remainder.length())));
        }
        if (original.isBlank()) notes.add("Blank prompt: using a neutral grounded settlement.");
        return new PromptAnalysis(original, subject, terrain, style, layout, List.copyOf(features),
            List.copyOf(excluded), notes);
    }

    private static void collect(String text, Set<String> result) {
        FEATURES.forEach((alias, feature) -> { if (word(text, alias)) result.add(feature); });
    }

    private static boolean word(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private static Map<String, String> aliases() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String feature : List.of("building", "tower", "garden", "wall", "water", "palace", "island", "waterfall", "bridge", "cloud")) {
            result.put(feature, feature);
            result.put(feature + "s", feature);
        }
        result.put("house", "building"); result.put("houses", "building");
        result.put("trees", "trees"); result.put("tree", "trees"); result.put("park", "garden");
        result.put("parks", "garden"); result.put("courtyard", "garden");
        result.put("cherry", "trees");
        result.put("floating", "island");
        return result;
    }
}
