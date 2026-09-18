package com.annaschneider.minecraft1.instantbuilder;

import com.annaschneider.minecraft1.domain.Blueprint;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TemplateRegistry {
    private final Map<String, TemplateDefinition> templates;

    public TemplateRegistry() {
        TemplateBlueprintFactory factory = new TemplateBlueprintFactory();
        Map<String, TemplateDefinition> map = new LinkedHashMap<>();
        register(map, new TemplateDefinition("house", "House", factory::house));
        register(map, new TemplateDefinition("castle", "Castle", factory::castle));
        register(map, new TemplateDefinition("temple", "Temple", factory::temple));
        register(map, new TemplateDefinition("village", "Village", factory::village));
        templates = Map.copyOf(map);
    }

    public Collection<String> ids() {
        return templates.keySet();
    }

    public Optional<Blueprint> create(String id) {
        if (id == null) {
            return Optional.empty();
        }
        TemplateDefinition template = templates.get(id.toLowerCase());
        return template == null ? Optional.empty() : Optional.of(template.factory().get());
    }

    public Collection<TemplateDefinition> definitions() {
        return templates.values();
    }

    private static void register(Map<String, TemplateDefinition> map, TemplateDefinition definition) {
        map.put(definition.id(), definition);
    }
}
