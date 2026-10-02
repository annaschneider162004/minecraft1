package com.annaschneider.minecraft1.video;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the footage for every scene. Scenes refer to build progress; milestones (progress + footage time) map
 * progress to a time in the footage, otherwise progress is spread evenly over the footage. Clips are treated as one
 * continuous recording in list order. Real-time scenes are trimmed around their moment, timelapse scenes are sped up
 * (capped at {@link #MAX_SPEED}x) and every scene fades in and out through black.
 */
public final class SegmentPlanner {
    public static final double MAX_SPEED = 200;
    static final double FADE_SECONDS = 0.5;
    static final double MIN_PIECE_SECONDS = 0.4;

    public List<PlannedSegment> plan(Storyboard storyboard, BuildContext context, List<FootageClip> footage) {
        List<PlannedSegment> segments = new ArrayList<>();
        double total = footage.stream().mapToDouble(FootageClip::durationSeconds).sum();
        for (Scene scene : storyboard.scenes()) {
            double duration = scene.durationSeconds();
            double fade = Math.min(FADE_SECONDS, duration / 4);
            if (footage.isEmpty()) {
                segments.add(new PlannedSegment(scene.index(), null, 0, 0, duration, fade, fade));
            } else if (scene.isTimelapse()) {
                segments.addAll(timelapse(scene, context, footage, total, fade));
            } else {
                segments.add(realTime(scene, context, footage, total, fade));
            }
        }
        return segments;
    }

    private static PlannedSegment realTime(Scene scene, BuildContext context, List<FootageClip> footage, double total, double fade) {
        double at = timeAt(scene.fromProgress(), context, total);
        Located located = locate(footage, at);
        double clipLength = located.clip.durationSeconds();
        double window = Math.min(scene.durationSeconds(), clipLength);
        double start = switch (scene.kind()) {
            case INTRO -> located.offset;
            case FINALE -> located.offset - window;
            default -> located.offset - window / 2;
        };
        start = Math.max(0, Math.min(clipLength - window, start));
        return new PlannedSegment(scene.index(), located.clip.file(), round(start), round(window), scene.durationSeconds(), fade, fade);
    }

    private static List<PlannedSegment> timelapse(Scene scene, BuildContext context, List<FootageClip> footage, double total,
                                                  double fade) {
        double from = timeAt(scene.fromProgress(), context, total);
        double to = timeAt(scene.toProgress(), context, total);
        if (to - from < 0.5) {
            double span = Math.min(scene.durationSeconds(), total);
            double middle = (from + to) / 2;
            from = Math.max(0, Math.min(total - span, middle - span / 2));
            to = from + span;
        }
        double span = to - from;
        List<double[]> pieces = new ArrayList<>(); // clip index, start in clip, length
        double clipStart = 0;
        for (int i = 0; i < footage.size(); i++) {
            double clipEnd = clipStart + footage.get(i).durationSeconds();
            double overlapStart = Math.max(from, clipStart);
            double overlapEnd = Math.min(to, clipEnd);
            if (overlapEnd - overlapStart > 0.01) {
                pieces.add(new double[] {i, overlapStart - clipStart, overlapEnd - overlapStart});
            }
            clipStart = clipEnd;
        }
        double duration = scene.durationSeconds();
        List<double[]> kept = pieces.stream().filter(p -> p[2] / span * duration >= MIN_PIECE_SECONDS).toList();
        if (kept.isEmpty()) {
            kept = List.of(pieces.stream().max((a, b) -> Double.compare(a[2], b[2])).orElseThrow());
        }
        double keptSource = kept.stream().mapToDouble(p -> p[2]).sum();
        List<PlannedSegment> result = new ArrayList<>();
        double used = 0;
        for (int i = 0; i < kept.size(); i++) {
            double[] piece = kept.get(i);
            double output = i == kept.size() - 1 ? duration - used : round(piece[2] / keptSource * duration);
            used += output;
            double source = Math.min(piece[2], output * MAX_SPEED);
            double start = piece[1] + (piece[2] - source) / 2;
            result.add(new PlannedSegment(scene.index(), footage.get((int) piece[0]).file(), round(start), round(source), output,
                i == 0 ? fade : 0, i == kept.size() - 1 ? fade : 0));
        }
        return result;
    }

    /**
     * Footage time for a build progress (0..1). Uses milestones with times when present (interpolated linearly, scaled
     * down if they run past the end of the footage), otherwise spreads progress evenly over the footage.
     */
    static double timeAt(double progress, BuildContext context, double total) {
        List<BuildMilestone> timed = context.timedMilestones();
        if (timed.isEmpty()) {
            return progress * total;
        }
        double latest = timed.stream().mapToDouble(BuildMilestone::timeSeconds).max().orElse(0);
        double scale = latest > total ? total / latest : 1;
        double percent = progress * 100;
        double previousPercent = 0;
        double previousTime = 0;
        for (BuildMilestone milestone : timed) {
            double time = milestone.timeSeconds() * scale;
            if (percent <= milestone.percent()) {
                double width = milestone.percent() - previousPercent;
                double t = width <= 0 ? time : previousTime + (time - previousTime) * (percent - previousPercent) / width;
                return clamp(t, total);
            }
            previousPercent = milestone.percent();
            previousTime = time;
        }
        double width = 100 - previousPercent;
        double t = width <= 0 ? previousTime : previousTime + (total - previousTime) * (percent - previousPercent) / width;
        return clamp(t, total);
    }

    private static Located locate(List<FootageClip> footage, double time) {
        double start = 0;
        for (FootageClip clip : footage) {
            if (time < start + clip.durationSeconds()) {
                return new Located(clip, time - start);
            }
            start += clip.durationSeconds();
        }
        FootageClip last = footage.get(footage.size() - 1);
        return new Located(last, last.durationSeconds());
    }

    private static double clamp(double value, double total) {
        return Math.max(0, Math.min(total, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private record Located(FootageClip clip, double offset) {
    }
}
