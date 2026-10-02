package com.annaschneider.minecraft1.video;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentPlannerTest {
    private static final Path A = Path.of("a.mp4");
    private static final Path B = Path.of("b.mp4");

    private static Storyboard story() {
        return new Storyboard("t", "p", "en", "template", List.of(
            new Scene(0, SceneKind.INTRO, "Opening", "x", 0, 0, 6),
            new Scene(1, SceneKind.PROGRESS, "Half", "x", 0.5, 0.5, 6),
            new Scene(2, SceneKind.TIMELAPSE, "Fast", "x", 0, 1, 10),
            new Scene(3, SceneKind.FINALE, "End", "x", 1, 1, 8)));
    }

    @Test
    void withoutFootageEverySceneBecomesATitleCard() {
        List<PlannedSegment> segments = new SegmentPlanner().plan(story(), BuildContext.empty(), List.of());
        assertEquals(4, segments.size());
        assertTrue(segments.stream().allMatch(PlannedSegment::isTitleCard));
        assertEquals(30, segments.stream().mapToDouble(PlannedSegment::outputDuration).sum(), 1e-9);
    }

    @Test
    void cutsRealTimeShotsAndSpeedsUpTheTimelapse() {
        List<PlannedSegment> segments = new SegmentPlanner().plan(story(), BuildContext.empty(), List.of(new FootageClip(A, 200)));
        assertEquals(4, segments.size());
        PlannedSegment intro = segments.get(0);
        assertEquals(0, intro.sourceStart());
        assertEquals(6, intro.sourceDuration());
        assertEquals(1, intro.speed(), 1e-9);
        assertEquals(0.5, intro.fadeIn());
        PlannedSegment half = segments.get(1);
        assertEquals(97, half.sourceStart(), 1e-9);
        PlannedSegment timelapse = segments.get(2);
        assertEquals(0, timelapse.sourceStart());
        assertEquals(200, timelapse.sourceDuration(), 1e-9);
        assertEquals(20, timelapse.speed(), 1e-9);
        PlannedSegment finale = segments.get(3);
        assertEquals(192, finale.sourceStart(), 1e-9);
        assertEquals(200, finale.sourceStart() + finale.sourceDuration(), 1e-9);
    }

    @Test
    void milestonesPlaceScenesAtTheRightMoment() {
        // recording started 20 s before the build; build was half done at 60 s and finished at 100 s
        BuildContext context = new BuildContext("palace", List.of(new BuildMilestone(20, 0, "started"),
            new BuildMilestone(60, 50, "50%"), new BuildMilestone(100, 100, "completed")), List.of(), 0);
        assertEquals(20, SegmentPlanner.timeAt(0, context, 120), 1e-9);
        assertEquals(60, SegmentPlanner.timeAt(0.5, context, 120), 1e-9);
        assertEquals(40, SegmentPlanner.timeAt(0.25, context, 120), 1e-9);
        assertEquals(100, SegmentPlanner.timeAt(1, context, 120), 1e-9);
        List<PlannedSegment> segments = new SegmentPlanner().plan(story(), context, List.of(new FootageClip(A, 120)));
        assertEquals(20, segments.get(0).sourceStart(), 1e-9);
        assertEquals(57, segments.get(1).sourceStart(), 1e-9);
        assertEquals(20, segments.get(2).sourceStart(), 1e-9);
        assertEquals(80, segments.get(2).sourceDuration(), 1e-9);
        assertEquals(92, segments.get(3).sourceStart(), 1e-9);
        // milestone times beyond the footage (e.g. a shortened render) are scaled into it
        assertEquals(36, SegmentPlanner.timeAt(0.5, context, 60), 1e-9);
    }

    @Test
    void timelapseSpansSeveralClipsAndShortClipsAreSlowedDown() {
        List<FootageClip> clips = List.of(new FootageClip(A, 30), new FootageClip(B, 90));
        List<PlannedSegment> segments = new SegmentPlanner().plan(story(), BuildContext.empty(), clips);
        List<PlannedSegment> timelapse = segments.stream().filter(s -> s.sceneIndex() == 2).toList();
        assertEquals(2, timelapse.size());
        assertEquals(A, timelapse.get(0).clip());
        assertEquals(B, timelapse.get(1).clip());
        assertEquals(10, timelapse.stream().mapToDouble(PlannedSegment::outputDuration).sum(), 1e-9);
        assertEquals(12, timelapse.get(0).speed(), 1e-6);
        assertEquals(0, timelapse.get(0).fadeOut());
        assertEquals(0, timelapse.get(1).fadeIn());

        List<PlannedSegment> tiny = new SegmentPlanner().plan(story(), BuildContext.empty(), List.of(new FootageClip(A, 3)));
        assertEquals(3, tiny.get(0).sourceDuration(), 1e-9);
        assertEquals(0.5, tiny.get(0).speed(), 1e-9);
    }
}
