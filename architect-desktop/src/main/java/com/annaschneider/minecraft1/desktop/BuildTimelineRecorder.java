package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.JobStatus;
import com.annaschneider.minecraft1.link.PlanSummary;
import com.annaschneider.minecraft1.link.RegionBox;
import com.annaschneider.minecraft1.video.BuildContext;
import com.annaschneider.minecraft1.video.BuildMilestone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Remembers the timeline of the latest build for the Video Studio: progress milestones (start, every 10 %, end) with
 * their time since the recording started (or since the build started when nothing was being recorded), plus the plan's
 * region types and block count. It only observes progress messages the app already receives.
 */
final class BuildTimelineRecorder {
    private static final double STEP_PERCENT = 10;

    private final LongSupplier clock;
    private final List<BuildMilestone> milestones = new ArrayList<>();
    private List<String> sections = List.of();
    private long plannedBlocks;
    private boolean recording;
    private long footageStart = -1;
    private long jobId = -1;
    private String buildName = "";
    private String lastState = "";
    private double lastStep = -1;
    private long blocksChanged;

    BuildTimelineRecorder(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized void recordingStarted() {
        recording = true;
        footageStart = clock.getAsLong();
    }

    synchronized void recordingStopped() {
        recording = false;
    }

    synchronized void plan(PlanSummary plan) {
        if (plan == null) {
            return;
        }
        sections = plan.regions().stream().map(RegionBox::type).filter(type -> type != null && !type.isBlank())
            .map(type -> type.toLowerCase(Locale.ROOT).replace('_', ' ')).distinct().toList();
        plannedBlocks = plan.estimatedBlocks();
    }

    synchronized void onJob(JobStatus status) {
        if (status == null || !"build".equals(status.kind()) || "queued".equals(status.state())) {
            return;
        }
        long now = clock.getAsLong();
        if (status.jobId() != jobId) {
            jobId = status.jobId();
            buildName = status.name() == null ? "" : status.name();
            milestones.clear();
            lastState = "";
            lastStep = -1;
            if (!recording || footageStart < 0) {
                footageStart = now;
            }
        }
        blocksChanged = Math.max(blocksChanged, status.blocksChanged());
        double seconds = (now - footageStart) / 1000.0;
        double step = Math.floor(status.percent() / STEP_PERCENT) * STEP_PERCENT;
        if (!status.state().equals(lastState) && !"running".equals(status.state()) && !"paused".equals(status.state())) {
            milestones.add(new BuildMilestone(seconds, status.percent(), status.state()));
        } else if ("running".equals(status.state()) && (lastStep < 0 || step > lastStep)) {
            milestones.add(new BuildMilestone(seconds, lastStep < 0 ? status.percent() : step,
                lastStep < 0 ? "started" : String.format(Locale.ROOT, "%.0f%%", step)));
        }
        if ("running".equals(status.state())) {
            lastStep = Math.max(lastStep, step);
        }
        lastState = status.state();
    }

    synchronized boolean hasBuild() {
        return jobId >= 0;
    }

    synchronized BuildContext snapshot() {
        long blocks = blocksChanged > 0 ? blocksChanged : plannedBlocks;
        return new BuildContext(buildName, List.copyOf(milestones), sections, blocks);
    }

    synchronized String describe() {
        if (jobId < 0) {
            return sections.isEmpty() ? "No build recorded yet - scenes are spread evenly over the footage."
                : "Plan parts: " + String.join(", ", sections) + " (no build progress recorded yet).";
        }
        return String.format(Locale.ROOT, "Build '%s': %d milestones%s", buildName, milestones.size(),
            sections.isEmpty() ? "" : ", parts: " + String.join(", ", sections));
    }
}
