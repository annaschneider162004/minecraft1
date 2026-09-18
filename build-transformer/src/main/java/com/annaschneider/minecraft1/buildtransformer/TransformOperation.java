package com.annaschneider.minecraft1.buildtransformer;

import java.util.Locale;
import java.util.Optional;

public enum TransformOperation {
    UPGRADE,
    STYLE,
    DAMAGE;

    public static Optional<TransformOperation> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TransformOperation.valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
