package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.SceneRegion;
import java.util.List;

public interface LayoutGenerator {
    LayoutType layout();
    String id();
    int version();
    boolean compatible(PromptAnalysis prompt, StylePreset style);
    List<SceneRegion> generate(PromptAnalysis prompt, StylePreset style, int scale, long seed);
}
