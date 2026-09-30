package com.annaschneider.minecraft1.largebuild.npc;

import com.annaschneider.minecraft1.domain.Vec3i;

import java.util.Optional;
import java.util.UUID;

/**
 * A (future) NPC that performs part of a build. The Fabric implementation will wrap a custom entity that walks to its
 * sections and plays placement animations while the {@link com.annaschneider.minecraft1.largebuild.engine.BuildQueue}
 * performs the actual, budgeted block writes.
 */
public interface BuildAgent {
    UUID id();

    String name();

    AgentRole role();

    Optional<Vec3i> position();

    boolean isIdle();

    void assign(AgentTask task);
}
