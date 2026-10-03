package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.GenerationMetadata;
import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;
import com.annaschneider.minecraft1.largebuild.scene.RegionType;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Offline text/image planner with reproducible compatible weighted layout selection. */
public final class MultiLayoutScenePlanner implements ScenePlanner {
    public static final String ID = "multi-layout-v1";
    private static final SecureRandom SEEDS = new SecureRandom();
    private final OfflinePromptParser parser = new OfflinePromptParser();
    private final LayoutGeneratorRegistry registry = new LayoutGeneratorRegistry();

    @Override public String id() { return ID; }

    public ScenePlan planText(String prompt, PlanOptions options) {
        PromptAnalysis parsed = parser.parse(prompt);
        long seed = options.seed() == null ? SEEDS.nextLong() : options.seed();
        return generate(parsed, options, seed, "prompt:" + parsed.prompt(), List.of());
    }

    @Override public ScenePlan plan(ImageAnalysis analysis, PlanOptions options) {
        if (options.layout() == LayoutType.LEGACY) {
            return new DeterministicScenePlanner().plan(withSeed(analysis,
                options.seed() == null ? analysis.seed() : options.seed()), options);
        }
        String description = analysis.features().stream().map(type -> type.id()).sorted()
            .collect(java.util.stream.Collectors.joining(" "));
        String style = options.style() == null ? analysis.style() : options.style();
        PromptAnalysis parsed = parser.parse(description);
        if (style != null && !style.isBlank()) {
            parsed = new PromptAnalysis(parsed.prompt(), parsed.subject(), parsed.terrain(), StylePreset.fromId(style),
                parsed.layout(), parsed.features(), parsed.exclusions(), parsed.diagnostics());
        }
        return generate(parsed, options, options.seed() == null ? analysis.seed() : options.seed(),
            analysis.image() == null ? "image" : analysis.image().display(), analysis.notes());
    }

    private ScenePlan generate(PromptAnalysis prompt, PlanOptions options, long seed, String source, List<String> initialNotes) {
        StylePreset style = options.style() == null || options.style().isBlank() ? prompt.style() : StylePreset.fromId(options.style());
        List<String> notes = new ArrayList<>(initialNotes);
        notes.addAll(prompt.diagnostics());
        LayoutType requested = options.layout() != LayoutType.AUTO ? options.layout() : prompt.layout();
        long selectionSeed = SceneSeeds.selectionSeed(seed);
        if (requested == LayoutType.LEGACY) {
            if (!prompt.exclusions().isEmpty()) {
                throw new IllegalArgumentException("LEGACY cannot honor exclusions " + prompt.exclusions()
                    + "; choose a grounded layout instead.");
            }
            long generatorSeed = SceneSeeds.generatorSeed(seed, LayoutType.LEGACY);
            ImageAnalysis legacyAnalysis = new ImageAnalysis(null, ID, generatorSeed, 1920, 1920,
                EnumSet.of(RegionType.PALACE_CORE, RegionType.BRIDGE, RegionType.TERRACE, RegionType.WATERFALL,
                    RegionType.ISLAND, RegionType.PATH, RegionType.GARDEN, RegionType.CHERRY_TREES, RegionType.CLOUDS),
                style.id(), 0, notes);
            ScenePlan legacy = new DeterministicScenePlanner().plan(legacyAnalysis, options);
            notes = new ArrayList<>(legacy.notes());
            notes.add("Selected layout: LEGACY; style " + style.id() + "; seed " + seed + "; generator "
                + DeterministicScenePlanner.ID + " v1; original floating-kingdom compatibility geometry and palette.");
            GenerationMetadata metadata = new GenerationMetadata(LayoutType.LEGACY, DeterministicScenePlanner.ID, 1,
                style.id(), prompt.prompt(), prompt.subject(), prompt.terrain(), prompt.features(), prompt.exclusions(),
                Map.of(), Map.of("scale", Integer.toString(options.scale()), "selection",
                    options.layout() == LayoutType.LEGACY ? "option" : "prompt",
                    "selectionSeed", Long.toString(selectionSeed), "generatorSeed", Long.toString(generatorSeed)));
            return new ScenePlan(ScenePlan.FORMAT_VERSION, options.planId(), legacy.title(), source, ID,
                style.id(), seed, options.scale(), legacy.regions(), notes, metadata);
        }
        for (String feature : prompt.features()) {
            if (!style.supportedFeatures().contains(feature)) {
                notes.add("Unsupported feature '" + feature + "' for style " + style.id() + "; omitted.");
            } else if (feature.equals("waterfall") && prompt.exclusions().contains("water")) {
                notes.add("Feature 'waterfall' omitted because water is excluded.");
            }
        }
        List<LayoutGenerator> compatible = registry.generators().stream()
            .filter(generator -> generator.compatible(prompt, style)).toList();
        EnumMap<LayoutType, Double> filtered = new EnumMap<>(LayoutType.class);
        Map<LayoutType, Double> weights = style.weights();
        weights.putAll(options.weights());
        for (LayoutGenerator generator : compatible) filtered.put(generator.layout(), weights.getOrDefault(generator.layout(), 0.0));
        // Custom weights are validated even for explicit layouts; default style weights only govern AUTO selection.
        double max = filtered.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if ((requested == LayoutType.AUTO || !options.weights().isEmpty()) && (max <= 0 || !Double.isFinite(max))) {
            throw new IllegalArgumentException("Layout weights have no positive total after compatible filtering for terrain '"
                + prompt.terrain() + "' and exclusions " + prompt.exclusions() + ".");
        }
        LayoutGenerator selected;
        if (requested != LayoutType.AUTO) {
            selected = registry.get(requested);
            if (!selected.compatible(prompt, style)) {
                throw new IllegalArgumentException("Layout " + requested + " is incompatible with terrain '"
                    + prompt.terrain() + "' or exclusions " + prompt.exclusions() + ".");
            }
        } else {
            double total = filtered.values().stream().mapToDouble(weight -> weight / max).sum();
            double choice = new SplittableRandom(selectionSeed).nextDouble(total);
            selected = null;
            for (LayoutGenerator candidate : compatible) {
                choice -= filtered.get(candidate.layout()) / max;
                if (choice < 0) { selected = candidate; break; }
            }
            if (selected == null) throw new IllegalStateException("Could not select a weighted layout.");
        }
        long generatorSeed = SceneSeeds.generatorSeed(seed, selected.layout());
        var regions = selected.generate(prompt, style, options.scale(), generatorSeed);
        GenerationMetadata metadata = new GenerationMetadata(selected.layout(), selected.id(), selected.version(),
            style.id(), prompt.prompt(), prompt.subject(), prompt.terrain(), prompt.features(), prompt.exclusions(),
            filtered, Map.of("scale", Integer.toString(options.scale()), "selection", requested == LayoutType.AUTO ? "weighted" :
                options.layout() == LayoutType.AUTO ? "prompt" : "option", "selectionSeed", Long.toString(selectionSeed),
                "generatorSeed", Long.toString(generatorSeed)));
        notes.add("Offline bounded prompt parser; unsupported vocabulary is not interpreted.");
        notes.add("Selected layout: " + selected.layout() + "; style " + style.id() + "; seed " + seed
            + "; generator " + selected.id() + " v" + selected.version() + ".");
        return new ScenePlan(ScenePlan.FORMAT_VERSION, options.planId(), prompt.subject() + " — " + selected.layout(),
            source, ID, style.id(), seed, options.scale(), regions, notes, metadata);
    }

    private static ImageAnalysis withSeed(ImageAnalysis a, long seed) {
        return new ImageAnalysis(a.image(), a.providerId(), seed, a.width(), a.height(), a.features(), a.style(), a.confidence(), a.notes());
    }
}
