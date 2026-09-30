package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.RegionType;

import java.util.List;
import java.util.Set;

/**
 * Provider output: what a (heuristic or AI) analyser believes the image contains. Planners only depend on this record,
 * never on the analyser implementation.
 *
 * @param features   region types the scene should contain
 * @param style      free-form style hint (e.g. {@code white_gold}, {@code dark_fantasy})
 * @param confidence 0..1, how much the analysis is grounded in actual image content
 */
public record ImageAnalysis(ImageReference image, String providerId, long seed, int width, int height,
                            Set<RegionType> features, String style, double confidence, List<String> notes) {
    public ImageAnalysis {
        if (features == null || features.isEmpty()) {
            throw new IllegalArgumentException("Image analysis must report at least one feature.");
        }
        features = Set.copyOf(features);
        notes = notes == null ? List.of() : List.copyOf(notes);
        width = width > 0 ? width : 1920;
        height = height > 0 ? height : 1080;
        confidence = Math.max(0, Math.min(1, confidence));
    }

    public double aspectRatio() {
        return (double) width / height;
    }
}
