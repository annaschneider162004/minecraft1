package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.domain.Vec3i;

public record PlacedBlock(Vec3i position, String previousState, String placedState) {
}
