package com.annaschneider.minecraft1.largebuild.npc;

import java.util.Arrays;

/**
 * A slice of a build assigned to one agent: packed section keys (ascending, whole chunk columns only).
 */
public record AgentTask(long jobId, int agentIndex, AgentRole role, long[] sectionKeys) {
    public AgentTask {
        sectionKeys = sectionKeys.clone();
    }

    public int sectionCount() {
        return sectionKeys.length;
    }

    @Override
    public long[] sectionKeys() {
        return sectionKeys.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AgentTask task && task.jobId == jobId && task.agentIndex == agentIndex
            && task.role == role && Arrays.equals(task.sectionKeys, sectionKeys);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Long.hashCode(jobId) + agentIndex) + Arrays.hashCode(sectionKeys);
    }

    @Override
    public String toString() {
        return "AgentTask[job=" + jobId + ", agent=" + agentIndex + ", role=" + role + ", sections=" + sectionKeys.length + "]";
    }
}
