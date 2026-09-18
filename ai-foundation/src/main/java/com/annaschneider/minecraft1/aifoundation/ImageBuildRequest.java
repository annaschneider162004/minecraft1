package com.annaschneider.minecraft1.aifoundation;

public record ImageBuildRequest(String imageReference, String prompt, int maxBlocks) {
    public ImageBuildRequest {
        if ((imageReference == null || imageReference.isBlank()) && (prompt == null || prompt.isBlank())) {
            throw new IllegalArgumentException("Provide either imageReference or prompt.");
        }
        if (maxBlocks < 1 || maxBlocks > 100_000) {
            throw new IllegalArgumentException("maxBlocks must be in range 1..100000.");
        }
    }
}
