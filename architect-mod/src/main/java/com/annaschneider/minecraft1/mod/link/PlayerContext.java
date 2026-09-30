package com.annaschneider.minecraft1.mod.link;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.mod.runtime.BlockWorld;

/** A player the desktop app acts for: identity, the world they are in and where builds are anchored. */
public record PlayerContext(java.util.UUID id, String name, BlockWorld world, Vec3i position) {
}
