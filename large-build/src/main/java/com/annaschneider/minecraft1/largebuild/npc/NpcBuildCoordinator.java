package com.annaschneider.minecraft1.largebuild.npc;

import com.annaschneider.minecraft1.largebuild.engine.BuildListener;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;

import java.util.List;

/**
 * Coordinates several {@link BuildAgent}s over one build job. It listens to the build engine (section completed,
 * job finished) to move agents and trigger animations; it never writes blocks itself, so tick safety is unchanged.
 */
public interface NpcBuildCoordinator extends BuildListener {
    void register(BuildAgent agent);

    void unregister(BuildAgent agent);

    List<BuildAgent> agents();

    /** Splits the job's sections between the registered agents and assigns them. */
    List<AgentTask> distribute(JobProgress job, long[] sortedSectionKeys);
}
