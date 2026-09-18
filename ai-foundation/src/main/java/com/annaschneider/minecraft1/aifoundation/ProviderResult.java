package com.annaschneider.minecraft1.aifoundation;

import com.annaschneider.minecraft1.domain.Blueprint;

public record ProviderResult(Blueprint blueprint, String summary, boolean fallbackUsed) {
}
