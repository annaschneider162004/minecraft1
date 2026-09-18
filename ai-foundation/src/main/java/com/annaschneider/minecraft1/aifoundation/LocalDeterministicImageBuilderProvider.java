package com.annaschneider.minecraft1.aifoundation;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.instantbuilder.TemplateRegistry;

import java.util.List;

public final class LocalDeterministicImageBuilderProvider implements RealImageBuilderProvider {
    private final TemplateRegistry templates = new TemplateRegistry();

    @Override
    public ProviderResult createBlueprint(ImageBuildRequest request) {
        List<String> ids = templates.ids().stream().toList();
        String key = (request.imageReference() == null ? "" : request.imageReference()) + "|" + (request.prompt() == null ? "" : request.prompt());
        int index = Math.floorMod(key.hashCode(), ids.size());
        String selected = ids.get(index);
        Blueprint blueprint = templates.create(selected).orElseThrow(() -> new IllegalStateException("Template registry misconfigured."));
        return new ProviderResult(blueprint, "Local deterministic image fallback mapped input to template: " + selected, true);
    }
}
