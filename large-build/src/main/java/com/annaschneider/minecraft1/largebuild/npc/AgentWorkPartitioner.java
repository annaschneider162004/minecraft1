package com.annaschneider.minecraft1.largebuild.npc;

import com.annaschneider.minecraft1.largebuild.blueprint.SectionKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits sorted section keys into contiguous, balanced slices for several agents. Because keys are ordered by chunk
 * column, each slice is a spatially coherent strip and a chunk column is never shared by two agents.
 */
public final class AgentWorkPartitioner {
    private AgentWorkPartitioner() {
    }

    public static List<AgentTask> partition(long jobId, long[] sortedSectionKeys, List<AgentRole> roles) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one agent is required.");
        }
        int agents = roles.size();
        List<AgentTask> tasks = new ArrayList<>(agents);
        int n = sortedSectionKeys.length;
        int start = 0;
        for (int a = 0; a < agents; a++) {
            int end = a == agents - 1 ? n : Math.max(start, (int) ((long) n * (a + 1) / agents));
            while (end > start && end < n && sameColumn(sortedSectionKeys[end - 1], sortedSectionKeys[end])) {
                end++;
            }
            long[] slice = new long[end - start];
            System.arraycopy(sortedSectionKeys, start, slice, 0, slice.length);
            tasks.add(new AgentTask(jobId, a, roles.get(a), slice));
            start = end;
        }
        return tasks;
    }

    private static boolean sameColumn(long a, long b) {
        SectionKey ka = SectionKey.unpack(a);
        SectionKey kb = SectionKey.unpack(b);
        return ka.chunkX() == kb.chunkX() && ka.chunkZ() == kb.chunkZ();
    }
}
