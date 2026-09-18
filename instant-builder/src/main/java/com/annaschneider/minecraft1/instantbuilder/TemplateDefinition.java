package com.annaschneider.minecraft1.instantbuilder;

import com.annaschneider.minecraft1.domain.Blueprint;

import java.util.function.Supplier;

public record TemplateDefinition(String id, String displayName, Supplier<Blueprint> factory) {
}
