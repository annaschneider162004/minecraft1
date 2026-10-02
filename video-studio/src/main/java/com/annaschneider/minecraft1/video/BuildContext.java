package com.annaschneider.minecraft1.video;

import java.util.Comparator;
import java.util.List;

/**
 * What is known about the build being filmed. Every field is optional so the pipeline also works on footage alone.
 *
 * @param buildName  plan or template name (e.g. "white-palace")
 * @param milestones progress milestones, ideally with times relative to the footage start
 * @param sections   names of the parts of the build (regions/sections such as "palace", "bridge", "waterfall")
 * @param blocks     estimated or changed block count (0 = unknown)
 */
public record BuildContext(String buildName, List<BuildMilestone> milestones, List<String> sections, long blocks) {
    public BuildContext {
        buildName = buildName == null ? "" : buildName.trim();
        milestones = milestones == null ? List.of()
            : milestones.stream().sorted(Comparator.comparingDouble(BuildMilestone::percent)).toList();
        sections = sections == null ? List.of() : sections.stream()
            .filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
        blocks = Math.max(0, blocks);
    }

    public static BuildContext empty() {
        return new BuildContext("", List.of(), List.of(), 0);
    }

    /** Milestones that carry a footage time, sorted by progress. */
    public List<BuildMilestone> timedMilestones() {
        return milestones.stream().filter(BuildMilestone::hasTime).toList();
    }
}
