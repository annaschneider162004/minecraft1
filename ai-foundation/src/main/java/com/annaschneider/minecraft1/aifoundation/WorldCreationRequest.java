package com.annaschneider.minecraft1.aifoundation;

public record WorldCreationRequest(String prompt, int maxRadius) {
    public WorldCreationRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank.");
        }
        if (maxRadius < 16 || maxRadius > 128) {
            throw new IllegalArgumentException("maxRadius must be in range 16..128.");
        }
    }
}
