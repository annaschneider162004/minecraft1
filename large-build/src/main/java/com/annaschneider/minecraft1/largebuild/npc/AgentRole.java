package com.annaschneider.minecraft1.largebuild.npc;

/**
 * Responsibilities a builder NPC can take in a multi-agent build.
 */
public enum AgentRole {
    PLANNER("Foreman"),
    BUILDER("Builder"),
    DECORATOR("Decorator"),
    ROAD_WORKER("Material Runner"),
    CAMERA_ASSISTANT("Camera Assistant");

    private final String displayName;

    AgentRole(String displayName) {
        this.displayName = displayName;
    }

    /** Name shown above the worker NPC in the world. */
    public String displayName() {
        return displayName;
    }
}
