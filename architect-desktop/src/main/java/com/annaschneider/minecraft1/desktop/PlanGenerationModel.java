package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.PlanGenerationOptions;
import com.annaschneider.minecraft1.link.LinkProtocolException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Headless, transactional input validation and once-per-selection seed resolution.
 * Entropy is used only to choose a new seed; geometry is generated from that captured seed by the server.
 */
final class PlanGenerationModel {
    private final LongSupplier seeds;
    private Inputs resolvedInputs;
    private PlanGenerationOptions resolved;

    PlanGenerationModel() {
        this(() -> ThreadLocalRandom.current().nextLong());
    }

    PlanGenerationModel(LongSupplier seeds) {
        this.seeds = Objects.requireNonNull(seeds);
    }

    record Inputs(String prompt, String name, int scale, String layout, String style, String seed, String weights) { }

    PlanGenerationOptions resolve(Inputs inputs) {
        PlanGenerationOptions validated = validate(inputs);
        if (inputs.equals(resolvedInputs)) {
            return resolved;
        }
        resolved = new PlanGenerationOptions(validated.layout(), validated.style(),
            validated.seed() == null ? seeds.getAsLong() : validated.seed(), validated.weights());
        resolvedInputs = inputs;
        return resolved;
    }

    long newVariation(Inputs inputs) {
        PlanGenerationOptions previous = resolve(inputs);
        long seed = seeds.getAsLong();
        if (seed == previous.seed()) {
            seed = seed == Long.MAX_VALUE ? Long.MIN_VALUE : seed + 1;
        }
        resolved = new PlanGenerationOptions(previous.layout(), previous.style(), seed, previous.weights());
        resolvedInputs = new Inputs(inputs.prompt(), inputs.name(), inputs.scale(), inputs.layout(), inputs.style(),
            Long.toString(seed), inputs.weights());
        return seed;
    }

    static PlanGenerationOptions validate(Inputs inputs) {
        String seedText = inputs.seed().trim();
        Long seed = null;
        if (!seedText.isEmpty()) {
            if (!seedText.matches("[+-]?[0-9]+")) {
                throw new IllegalArgumentException("Seed must be a signed 64-bit integer, or blank.");
            }
            try {
                seed = Long.valueOf(seedText);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Seed must be between " + Long.MIN_VALUE + " and " + Long.MAX_VALUE + ".");
            }
        }
        try {
            return new PlanGenerationOptions(auto(inputs.layout()), auto(inputs.style()), seed, parseWeights(inputs.weights()));
        } catch (LinkProtocolException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
    }

    static Map<String, Double> parseWeights(String text) {
        Map<String, Double> weights = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        for (String item : text.split(",", -1)) {
            String[] pair = item.trim().split("=", -1);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Weights must use layout=weight, e.g. RADIAL=1,LINEAR=2.");
            }
            String layout = pair[0].trim().toUpperCase(Locale.ROOT);
            if (!PlanGenerationOptions.WEIGHT_LAYOUTS.contains(layout)) {
                throw new IllegalArgumentException("Unknown weighted layout: " + pair[0].trim());
            }
            String value = pair[1].trim();
            if (!value.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) {
                throw new IllegalArgumentException("Weights must be finite, nonnegative decimal numbers.");
            }
            double weight;
            try {
                weight = Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid layout weight: " + value);
            }
            if (!Double.isFinite(weight) || weights.putIfAbsent(layout, weight) != null) {
                throw new IllegalArgumentException("Weights must be finite and each layout must appear only once.");
            }
        }
        return Map.copyOf(weights);
    }

    private static String auto(String value) {
        return value == null || value.equalsIgnoreCase("Auto") ? null : value;
    }
}
