package com.annaschneider.minecraft1.largebuild.image;

/**
 * Pluggable image analyser. The bundled {@link HeuristicImageAnalysisProvider} is offline and deterministic; a future
 * AI/computer-vision provider implements the same interface (run it off the server thread and cache the result).
 */
public interface ImageAnalysisProvider {
    String id();

    ImageAnalysis analyze(ImageReference image);
}
