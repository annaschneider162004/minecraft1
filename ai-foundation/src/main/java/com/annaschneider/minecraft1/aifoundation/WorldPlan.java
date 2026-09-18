package com.annaschneider.minecraft1.aifoundation;

import com.annaschneider.minecraft1.domain.Blueprint;

import java.util.List;

public record WorldPlan(List<String> steps, Blueprint previewBlueprint, boolean fallbackUsed) {
}
