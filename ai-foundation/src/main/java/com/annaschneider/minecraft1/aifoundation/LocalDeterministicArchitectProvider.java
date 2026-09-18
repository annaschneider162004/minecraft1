package com.annaschneider.minecraft1.aifoundation;

import com.annaschneider.minecraft1.buildtransformer.BuildTransformerService;
import com.annaschneider.minecraft1.buildtransformer.TransformOperation;
import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.instantbuilder.TemplateRegistry;

import java.util.Locale;

public final class LocalDeterministicArchitectProvider implements AIArchitectProvider {
    private final TemplateRegistry templates = new TemplateRegistry();
    private final BuildTransformerService transformer = new BuildTransformerService();

    @Override
    public ProviderResult createBlueprint(ArchitectRequest request) {
        String lower = request.prompt().toLowerCase(Locale.ROOT);
        String selected = lower.contains("castle") ? "castle" : lower.contains("temple") ? "temple" : lower.contains("village") ? "village" : "house";
        Blueprint blueprint = templates.create(selected).orElseThrow(() -> new IllegalStateException("Template registry misconfigured."));

        if (lower.contains("upgrade")) {
            blueprint = transformer.transform(TransformOperation.UPGRADE, selected, blueprint);
        } else if (lower.contains("damage") || lower.contains("ruin")) {
            blueprint = transformer.transform(TransformOperation.DAMAGE, selected, blueprint);
        } else if (!"default".equalsIgnoreCase(request.style())) {
            blueprint = transformer.transform(TransformOperation.STYLE, selected, blueprint);
        }

        return new ProviderResult(blueprint, "Local deterministic architect fallback selected template: " + selected, true);
    }
}
