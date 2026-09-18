package com.annaschneider.minecraft1.aifoundation;

public record ArchitectRequest(String prompt, String style, int maxBlocks) {
    public ArchitectRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank.");
        }
        if (maxBlocks < 1 || maxBlocks > 100_000) {
            throw new IllegalArgumentException("maxBlocks must be in range 1..100000.");
        }
        style = style == null ? "default" : style.trim();
    }
}
