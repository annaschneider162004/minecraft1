package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.record.FootageRecorder;
import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.PiperTtsEngine;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.WavInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportFlowTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T08:30:00Z"), ZoneOffset.UTC);

    @Test
    void autoExportRecordsAndNarratesInParallelThenMuxesAutomatically(@TempDir Path dir) throws Exception {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        CountDownLatch narrationStarted = new CountDownLatch(1);
        TestSupport.FakeTts tts = new TestSupport.FakeTts() {
            @Override
            public void synthesize(VoicePack voice, String text, Path output) throws NarrationException {
                narrationStarted.countDown();
                super.synthesize(voice, text, output);
            }
        };
        // the recording only finishes once narration has started: proves both jobs run at the same time
        FakeRecorder recorder = new FakeRecorder(() -> {
            if (!narrationStarted.await(10, TimeUnit.SECONDS)) {
                throw new VideoExportException("narration did not run in parallel");
            }
        });
        Fixture fixture = new Fixture(dir, ffmpeg, tts, recorder);

        FlowResult result = fixture.run(ExportMode.AUTO_EXPORT, TestSupport.voice(dir));

        assertTrue(result.ok(), String.valueOf(result.failure()));
        assertEquals(FlowStage.COMPLETE, result.stage());
        assertEquals(dir.resolve("out/sky-palace-20261002-083000.mp4"), result.video());
        assertEquals(result.video(), result.output());
        assertTrue(Files.isRegularFile(result.video()));
        assertEquals(dir.resolve("out/sky-palace-20261002-083000-recording.mp4"), result.recording());
        assertTrue(Files.isRegularFile(result.recording()), "valid intermediate outputs are kept");
        assertEquals(dir.resolve("out/sky-palace-20261002-083000-narration.wav"), result.narration());
        assertTrue(WavInfo.seconds(result.narration()) > 0);
        assertTrue(Files.readString(result.script()).contains("Opening"));
        assertEquals(1, recorder.calls);
        assertEquals(Math.ceil(fixture.story.totalSeconds()), recorder.seconds, 1e-9, "records as long as the story by default");

        List<List<String>> renders = ffmpeg.renderCommands();
        List<String> mux = renders.get(renders.size() - 1);
        assertTrue(mux.contains("aac") && mux.contains("-shortest"), mux.toString());
        assertTrue(renders.stream().anyMatch(c -> c.contains(result.recording().toString())), "the recording is the footage");

        List<FlowStage> stages = fixture.stages();
        assertEquals(List.of(FlowStage.CHECKING_DEPENDENCIES, FlowStage.RECORDING_AND_NARRATION, FlowStage.WAITING_FOR_REMAINING_JOB,
            FlowStage.MUXING, FlowStage.COMPLETE), stages);
        FlowStatus running = fixture.statuses.get(1);
        assertEquals("Recording video... + Generating narration...", running.label("en"));
        assertEquals("Recording video...", running.recordingLine("en"));
        assertEquals("Generating narration...", running.narrationLine("en"));
        assertEquals("Waiting for remaining job...", fixture.statuses.get(2).label("en"));
        assertEquals("Muxing audio and video...", fixture.statuses.get(3).label("en"));
        FlowStatus last = fixture.statuses.get(fixture.statuses.size() - 1);
        assertEquals("Export complete", last.label("en"));
        assertEquals(FlowStatus.JobState.DONE, last.recording());
        assertEquals(FlowStatus.JobState.DONE, last.narration());
        try (Stream<Path> left = Files.list(dir.resolve("tmp"))) {
            assertEquals(0, left.count(), "temporary files must be deleted");
        }
    }

    @Test
    void recordOnlyExportsTheRawVideoWithoutNarration(@TempDir Path dir) throws Exception {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        Fixture fixture = new Fixture(dir, ffmpeg, tts, new FakeRecorder(() -> { }));

        FlowResult result = fixture.run(ExportMode.RECORD_ONLY, null);

        assertTrue(result.ok(), String.valueOf(result.failure()));
        assertNull(result.video());
        assertNull(result.narration());
        assertEquals(result.recording(), result.output());
        assertTrue(Files.isRegularFile(result.recording()));
        assertTrue(tts.spoken.isEmpty(), "no narration in Record only");
        assertTrue(ffmpeg.renderCommands().isEmpty(), "no muxing in Record only");
        assertEquals(List.of(FlowStage.CHECKING_DEPENDENCIES, FlowStage.RECORDING_AND_NARRATION, FlowStage.COMPLETE), fixture.stages());
        assertEquals("Recording video...", fixture.statuses.get(1).label("en"));
        assertEquals("Narration: not used in this mode", fixture.statuses.get(1).narrationLine("en"));
    }

    @Test
    void narrateOnlyMakesANarrationTrackWithoutFfmpegOrRecording(@TempDir Path dir) throws Exception {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        FakeRecorder recorder = new FakeRecorder(() -> {
            throw new AssertionError("Narrate only must not record");
        });
        VideoPipeline pipeline = new VideoPipeline(StoryService.templatesOnly(), new Narrator(List.of(tts)), Optional::empty, CLOCK,
            dir.resolve("tmp"));
        Fixture fixture = new Fixture(dir, pipeline, recorder);

        FlowResult result = fixture.run(ExportMode.NARRATE_ONLY, TestSupport.voice(dir));

        assertTrue(result.ok(), String.valueOf(result.failure()));
        assertEquals(result.narration(), result.output());
        assertNull(result.video());
        assertNull(result.recording());
        assertEquals(fixture.story.scenes().size(), tts.spoken.size());
        // the track lines up with the (narration-fitted) story: one stretch of audio per scene
        assertTrue(WavInfo.seconds(result.narration()) >= fixture.story.totalSeconds() - 0.1, "track covers every scene");
        assertTrue(Files.readString(result.script()).contains("Opening"));
        assertTrue(Files.readString(result.subtitles()).contains("-->"));
        assertEquals(0, recorder.calls);
        assertEquals("Generating narration...", fixture.statuses.get(1).label("en"));
    }

    @Test
    void missingFfmpegFailsBeforeAnyJobStarts(@TempDir Path dir) {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        FakeRecorder recorder = new FakeRecorder(() -> { });
        VideoPipeline pipeline = new VideoPipeline(StoryService.templatesOnly(), new Narrator(List.of(tts)), Optional::empty, CLOCK,
            dir.resolve("tmp"));
        Fixture fixture = new Fixture(dir, pipeline, recorder);

        FlowResult result = fixture.run(ExportMode.AUTO_EXPORT, TestSupport.voice(dir));

        assertEquals(FlowStage.FAILED, result.stage());
        assertEquals(FlowFailure.Kind.FFMPEG_MISSING, result.failure().kind());
        assertEquals("Export failed: FFmpeg not found", fixture.statuses.get(fixture.statuses.size() - 1).label("en"));
        assertEquals("Xu\u1ea5t video th\u1ea5t b\u1ea1i: Kh\u00f4ng t\u00ecm th\u1ea5y FFmpeg",
            fixture.statuses.get(fixture.statuses.size() - 1).label("vi"));
        assertTrue(result.failure().describe("en").contains("https://ffmpeg.org"));
        assertEquals(0, recorder.calls);
        assertTrue(tts.spoken.isEmpty());
        assertEquals(List.of(FlowStage.CHECKING_DEPENDENCIES, FlowStage.FAILED), fixture.stages());
    }

    @Test
    void missingVoiceBackendOrVoiceCancelsAutoExport(@TempDir Path dir) {
        TestSupport.FakeTts tts = new TestSupport.FakeTts();
        tts.unavailable = PiperTtsEngine.MISSING_MESSAGE;
        FakeRecorder recorder = new FakeRecorder(() -> { });
        Fixture fixture = new Fixture(dir, new TestSupport.FakeFfmpeg(), tts, recorder);

        FlowResult noBackend = fixture.run(ExportMode.AUTO_EXPORT, TestSupport.voice(dir));
        assertEquals(FlowFailure.Kind.NARRATION_UNAVAILABLE, noBackend.failure().kind());
        assertTrue(noBackend.failure().detail().contains("Piper"));
        assertEquals("Export failed: Narration backend unavailable",
            new FlowStatus(FlowStage.FAILED, null, null, noBackend.failure()).label("en"));

        FlowResult noVoice = fixture.run(ExportMode.NARRATE_ONLY, null);
        assertEquals(FlowFailure.Kind.NARRATION_UNAVAILABLE, noVoice.failure().kind());
        assertTrue(noVoice.failure().detail().contains("No voice"));
        assertEquals(0, recorder.calls, "nothing is recorded when a dependency is missing");
        assertNull(noBackend.video());
    }

    @Test
    void recordingFailureStopsNarrationAndExportsNothing(@TempDir Path dir) throws IOException {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Boolean> narrationInterrupted = new AtomicReference<>(false);
        TestSupport.FakeTts tts = new TestSupport.FakeTts() {
            @Override
            public void synthesize(VoicePack voice, String text, Path output) throws NarrationException {
                try {
                    release.await(); // a long narration that is still running when the recording fails
                } catch (InterruptedException ex) {
                    narrationInterrupted.set(true);
                    throw new NarrationException("cancelled", ex);
                }
            }
        };
        FakeRecorder recorder = new FakeRecorder(() -> {
            throw new VideoExportException("capture device lost");
        });
        Fixture fixture = new Fixture(dir, ffmpeg, tts, recorder);

        FlowResult result = fixture.run(ExportMode.AUTO_EXPORT, TestSupport.voice(dir));
        release.countDown();

        assertEquals(FlowStage.FAILED, result.stage());
        assertEquals(FlowFailure.Kind.RECORDING_FAILED, result.failure().kind());
        assertTrue(result.failure().describe("en").contains("capture device lost"));
        FlowStatus last = fixture.statuses.get(fixture.statuses.size() - 1);
        assertEquals("Export failed: Recording failed", last.label("en"));
        assertEquals(FlowStatus.JobState.FAILED, last.recording());
        assertEquals(FlowStatus.JobState.CANCELLED, last.narration());
        assertTrue(narrationInterrupted.get(), "the other job is cancelled");
        assertNull(result.video());
        assertTrue(ffmpeg.renderCommands().isEmpty(), "no final video when a job fails");
        try (Stream<Path> files = Files.list(dir.resolve("out"))) {
            assertEquals(0, files.count(), "no half-written outputs");
        }
    }

    @Test
    void narrationFailureKeepsTheFinishedRecording(@TempDir Path dir) {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        AtomicReference<Fixture> holder = new AtomicReference<>();
        TestSupport.FakeTts tts = new TestSupport.FakeTts() {
            @Override
            public void synthesize(VoicePack voice, String text, Path output) throws NarrationException {
                // fail only after the recording has finished and the flow waits for the narration
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!holder.get().stages().contains(FlowStage.WAITING_FOR_REMAINING_JOB) && System.nanoTime() < end) {
                    Thread.onSpinWait();
                }
                throw new NarrationException("Piper crashed");
            }
        };
        Fixture fixture = new Fixture(dir, ffmpeg, tts, new FakeRecorder(() -> { }));
        holder.set(fixture);

        FlowResult result = fixture.run(ExportMode.AUTO_EXPORT, TestSupport.voice(dir));

        assertEquals(FlowFailure.Kind.NARRATION_FAILED, result.failure().kind());
        assertEquals("Export failed: Narration failed", "Export failed: " + result.failure().reason("en"));
        assertNull(result.video());
        assertTrue(ffmpeg.renderCommands().isEmpty());
        assertTrue(Files.isRegularFile(result.recording()), "a finished recording is kept for a retry");
        assertEquals(FlowStatus.JobState.DONE, fixture.statuses.get(fixture.statuses.size() - 1).recording());
    }

    @Test
    void muxFailureKeepsRecordingAndNarration(@TempDir Path dir) {
        TestSupport.FakeFfmpeg ffmpeg = new TestSupport.FakeFfmpeg();
        ffmpeg.failRendering = true;
        Fixture fixture = new Fixture(dir, ffmpeg, new TestSupport.FakeTts(), new FakeRecorder(() -> { }));

        FlowResult result = fixture.run(ExportMode.AUTO_EXPORT, TestSupport.voice(dir));

        assertEquals(FlowFailure.Kind.MUX_FAILED, result.failure().kind());
        assertEquals("Export failed: Failed to mux audio and video",
            fixture.statuses.get(fixture.statuses.size() - 1).label("en"));
        assertTrue(result.failure().detail().contains("Unknown encoder"));
        assertNull(result.video());
        assertTrue(Files.isRegularFile(result.recording()));
        assertTrue(Files.isRegularFile(result.narration()));
        assertTrue(fixture.stages().contains(FlowStage.MUXING));
    }

    @Test
    void interruptingTheFlowCancelsBothJobsAndDeletesThePartialRecording(@TempDir Path dir) throws Exception {
        CountDownLatch recordingStarted = new CountDownLatch(1);
        AtomicReference<Path> partial = new AtomicReference<>();
        FootageRecorder recorder = new FootageRecorder() {
            @Override
            public String name() {
                return "blocking recorder";
            }

            @Override
            public Optional<String> unavailableReason() {
                return Optional.empty();
            }

            @Override
            public void record(Path output, double seconds, Consumer<String> progress) throws VideoExportException {
                try {
                    Files.writeString(output, "partial");
                    partial.set(output);
                    recordingStarted.countDown();
                    new CountDownLatch(1).await();
                } catch (IOException | InterruptedException ex) {
                    throw new VideoExportException("stopped", ex);
                }
            }
        };
        Fixture fixture = new Fixture(dir, new TestSupport.FakeFfmpeg(), new TestSupport.FakeTts(), recorder);
        AtomicReference<FlowResult> result = new AtomicReference<>();
        Thread runner = new Thread(() -> result.set(fixture.run(ExportMode.AUTO_EXPORT, TestSupport.voice(dir))));
        runner.start();
        assertTrue(recordingStarted.await(10, TimeUnit.SECONDS));
        runner.interrupt();
        runner.join(20_000);

        assertFalse(runner.isAlive());
        assertEquals(FlowStage.CANCELLED, result.get().stage());
        assertNull(result.get().failure(), "cancelling is not a failure");
        assertNull(result.get().video());
        assertFalse(Files.exists(partial.get()), "the partial recording is deleted");
        assertEquals("Cancelled", fixture.statuses.get(fixture.statuses.size() - 1).label("en"));
    }

    @Test
    void statusLabelsMatchTheUiSpecificationInEnglishAndVietnamese() {
        assertEquals("Checking dependencies...", FlowStage.CHECKING_DEPENDENCIES.label("en"));
        assertEquals("\u0110ang ki\u1ec3m tra ph\u1ee5 thu\u1ed9c...", FlowStage.CHECKING_DEPENDENCIES.label("vi"));
        FlowStatus recording = new FlowStatus(FlowStage.RECORDING_AND_NARRATION, FlowStatus.JobState.RUNNING,
            FlowStatus.JobState.DONE, null);
        assertEquals("Recording video...", recording.label("en"));
        assertEquals("\u0110ang quay video...", recording.label("vi"));
        FlowStatus narrating = new FlowStatus(FlowStage.RECORDING_AND_NARRATION, FlowStatus.JobState.DONE,
            FlowStatus.JobState.RUNNING, null);
        assertEquals("Generating narration...", narrating.label("en"));
        assertEquals("\u0110ang t\u1ea1o gi\u1ecdng \u0111\u1ecdc...", narrating.label("vi"));
        assertEquals("Waiting for remaining job...", FlowStage.WAITING_FOR_REMAINING_JOB.label("en"));
        assertEquals("\u0110ang ch\u1edd t\u00e1c v\u1ee5 c\u00f2n l\u1ea1i...", FlowStage.WAITING_FOR_REMAINING_JOB.label("vi"));
        assertEquals("Muxing audio and video...", FlowStage.MUXING.label("en"));
        assertEquals("\u0110ang gh\u00e9p audio v\u00e0 video...", FlowStage.MUXING.label("vi"));
        assertEquals("Export complete", FlowStage.COMPLETE.label("en"));
        assertEquals("Xu\u1ea5t video ho\u00e0n t\u1ea5t", FlowStage.COMPLETE.label("vi"));
        FlowStatus failed = new FlowStatus(FlowStage.FAILED, null, null,
            new FlowFailure(FlowFailure.Kind.RECORDING_FAILED, "x"));
        assertEquals("Export failed: Recording failed", failed.label("en"));
        assertEquals("Xu\u1ea5t video th\u1ea5t b\u1ea1i: Quay video th\u1ea5t b\u1ea1i", failed.label("vi"));
        assertEquals("Auto-export when both complete", ExportMode.AUTO_EXPORT.toString());
        assertTrue(ExportMode.AUTO_EXPORT.records() && ExportMode.AUTO_EXPORT.narrates());
        assertFalse(ExportMode.RECORD_ONLY.narrates());
        assertFalse(ExportMode.NARRATE_ONLY.records());
    }

    // ------------------------------------------------------------------ helpers

    private interface Step {
        void run() throws Exception;
    }

    /** Writes a small fake video after running {@code before}. */
    private static final class FakeRecorder implements FootageRecorder {
        private final Step before;
        volatile int calls;
        volatile double seconds;

        FakeRecorder(Step before) {
            this.before = before;
        }

        @Override
        public String name() {
            return "fake recorder";
        }

        @Override
        public Optional<String> unavailableReason() {
            return Optional.empty();
        }

        @Override
        public void record(Path output, double seconds, Consumer<String> progress) throws VideoExportException {
            calls++;
            this.seconds = seconds;
            try {
                before.run();
                Files.writeString(output, "fake recording");
            } catch (VideoExportException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new VideoExportException(ex.getMessage(), ex);
            }
        }
    }

    private static final class Fixture {
        final Path dir;
        final VideoPipeline pipeline;
        final FootageRecorder recorder;
        final Storyboard story;
        final List<FlowStatus> statuses = new CopyOnWriteArrayList<>();

        Fixture(Path dir, TestSupport.FakeFfmpeg ffmpeg, TestSupport.FakeTts tts, FootageRecorder recorder) {
            this(dir, new VideoPipeline(StoryService.templatesOnly(), new Narrator(List.of(tts)),
                () -> Optional.of(new FfmpegTool(Path.of("ffmpeg"), ffmpeg)), CLOCK, dir.resolve("tmp")), recorder);
        }

        Fixture(Path dir, VideoPipeline pipeline, FootageRecorder recorder) {
            this.dir = dir;
            this.pipeline = pipeline;
            this.recorder = recorder;
            this.story = pipeline.writeStory(new StoryRequest("30 second video of a sky palace", null, null, null)).storyboard();
        }

        FlowResult run(ExportMode mode, VoicePack voice) {
            statuses.clear();
            return new ExportFlow(pipeline, recorder).run(new FlowRequest(mode, story, BuildContext.empty(), voice, 0,
                dir.resolve("out"), "Sky Palace!", null), new FlowListener() {
                    @Override
                    public void status(FlowStatus status) {
                        statuses.add(status);
                    }

                    @Override
                    public void progress(String message) {
                    }
                });
        }

        List<FlowStage> stages() {
            return statuses.stream().map(FlowStatus::stage).toList();
        }
    }
}
