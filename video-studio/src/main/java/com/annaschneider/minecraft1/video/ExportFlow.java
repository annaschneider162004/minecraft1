package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.FlowStatus.JobState;
import com.annaschneider.minecraft1.video.record.FootageRecorder;
import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.render.SubtitleWriter;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.NarrationTrackWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automatic voice-over export in three {@link ExportMode modes}. Stages: check dependencies (output folder, recorder,
 * FFmpeg, voice backend) -> run the recording and narration jobs in parallel -> wait for the remaining job -> mux the
 * narration into the recording with {@link VideoPipeline} -> done. If either job fails, the flow stops without a final
 * video; outputs that were already finished ({@code <stem>-recording.mp4}, {@code <stem>-narration.wav}) are kept in the
 * output folder. Interrupting the calling thread cancels the flow and stops both jobs.
 */
public final class ExportFlow {
    /** Longest screen recording: one hour. */
    public static final double MAX_RECORD_SECONDS = 3600;
    private static final double MIN_FOOTAGE_SECONDS = 0.5;

    private final VideoPipeline pipeline;
    private final FootageRecorder recorder;

    public ExportFlow(VideoPipeline pipeline, FootageRecorder recorder) {
        this.pipeline = pipeline;
        this.recorder = recorder;
    }

    /** Runs the flow on the calling thread (jobs run on two extra threads) and reports every status change. */
    public FlowResult run(FlowRequest request, FlowListener listener) {
        return new Run(request, listener).execute();
    }

    private final class Run {
        private final FlowRequest request;
        private final FlowListener listener;
        private final String language;
        private FlowStage stage = FlowStage.IDLE;
        private JobState recording;
        private JobState narration;
        private Path recordingFile;
        private Path narrationFile;
        private Narration narrated;
        private Path script;
        private Path subtitles;

        Run(FlowRequest request, FlowListener listener) {
            this.request = request;
            this.listener = listener;
            this.language = request.storyboard().language();
            this.recording = request.mode().records() ? JobState.PENDING : JobState.NOT_USED;
            this.narration = request.mode().narrates() ? JobState.PENDING : JobState.NOT_USED;
        }

        FlowResult execute() {
            ExportMode mode = request.mode();
            emit(FlowStage.CHECKING_DEPENDENCIES);
            listener.progress("Mode: " + mode.label() + ". " + FlowStage.CHECKING_DEPENDENCIES.label(language));
            FlowFailure problem = checkDependencies();
            if (problem != null) {
                return failed(problem);
            }
            Path work;
            try {
                work = Files.createTempDirectory("architect-video-");
            } catch (IOException ex) {
                return failed(new FlowFailure(FlowFailure.Kind.OUTPUT_FOLDER, "Cannot create a temporary folder: " + ex.getMessage()));
            }
            String stem = pipeline.stamped(request.baseName(), request.storyboard().title());
            Path recordTarget = request.outputFolder().resolve(stem + "-recording.mp4");
            Path narrationTarget = request.outputFolder().resolve(stem + "-narration.wav");
            ExecutorService jobs = Executors.newFixedThreadPool(2, new JobThreads());
            try {
                FlowResult failure = runJobs(jobs, work, recordTarget, narrationTarget);
                if (failure != null) {
                    return failure;
                }
                stopJobs(jobs);
                return finish(stem);
            } finally {
                stopJobs(jobs);
                if (recording != JobState.DONE) {
                    deleteQuietly(recordTarget); // never keep a half-written recording
                }
                VideoPipeline.deleteRecursively(work);
            }
        }

        private FlowFailure checkDependencies() {
            try {
                Files.createDirectories(request.outputFolder());
                Path probe = Files.createTempFile(request.outputFolder(), ".architect-write-test-", ".tmp");
                Files.delete(probe);
            } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                return new FlowFailure(FlowFailure.Kind.OUTPUT_FOLDER, request.outputFolder() + ": " + ex.getMessage());
            }
            if (request.mode().records()) {
                // the recording is checked (and, in auto-export, muxed) with FFmpeg
                if (pipeline.ffmpeg().isEmpty()) {
                    return new FlowFailure(FlowFailure.Kind.FFMPEG_MISSING, FfmpegTool.MISSING_MESSAGE);
                }
                Optional<String> recorderProblem = recorder.unavailableReason();
                if (recorderProblem.isPresent()) {
                    return new FlowFailure(FlowFailure.Kind.RECORDING_FAILED, "Recorder unavailable: " + recorderProblem.get());
                }
            }
            if (request.mode().narrates()) {
                if (request.voice() == null) {
                    return new FlowFailure(FlowFailure.Kind.NARRATION_UNAVAILABLE, "No voice is selected. Copy a Piper voice "
                        + "(.onnx + .onnx.json) into the voices folder, click Refresh and pick it.");
                }
                Optional<String> voiceProblem = pipeline.narrationUnavailable(request.voice());
                if (voiceProblem.isPresent()) {
                    return new FlowFailure(FlowFailure.Kind.NARRATION_UNAVAILABLE, voiceProblem.get());
                }
            }
            return null;
        }

        /** Starts the jobs in parallel and waits for both; returns a failed/cancelled result, or null when all are done. */
        private FlowResult runJobs(ExecutorService jobs, Path work, Path recordTarget, Path narrationTarget) {
            CompletionService<Runnable> done = new ExecutorCompletionService<>(jobs);
            List<Future<Runnable>> futures = new ArrayList<>();
            Future<Runnable> recordJob = null;
            if (request.mode().records()) {
                recording = JobState.RUNNING;
                recordJob = done.submit(recordingJob(recordTarget));
                futures.add(recordJob);
            }
            if (request.mode().narrates()) {
                narration = JobState.RUNNING;
                futures.add(done.submit(narrationJob(work, narrationTarget)));
            }
            emit(FlowStage.RECORDING_AND_NARRATION);
            try {
                for (int remaining = futures.size(); remaining > 0; remaining--) {
                    Future<Runnable> finished = done.take();
                    boolean isRecording = finished == recordJob;
                    try {
                        finished.get().run(); // stores the job's output on this thread
                    } catch (ExecutionException ex) {
                        if (isRecording) {
                            recording = JobState.FAILED;
                        } else {
                            narration = JobState.FAILED;
                        }
                        cancelRunning(futures);
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        return failed(new FlowFailure(isRecording ? FlowFailure.Kind.RECORDING_FAILED : FlowFailure.Kind.NARRATION_FAILED,
                            cause.getMessage() == null ? cause.toString() : cause.getMessage()));
                    }
                    if (isRecording) {
                        recording = JobState.DONE;
                        listener.progress("Recording saved: " + recordingFile);
                    } else {
                        narration = JobState.DONE;
                        listener.progress("Narration saved: " + narrationFile);
                    }
                    if (remaining > 1) {
                        emit(FlowStage.WAITING_FOR_REMAINING_JOB);
                        listener.progress(FlowStage.WAITING_FOR_REMAINING_JOB.label(language));
                    }
                }
                return null;
            } catch (InterruptedException ex) {
                cancelRunning(futures);
                stopJobs(jobs);
                Thread.currentThread().interrupt();
                emit(FlowStage.CANCELLED);
                listener.progress(FlowStage.CANCELLED.label(language));
                return result(FlowStage.CANCELLED, null, null, List.of());
            }
        }

        private Callable<Runnable> recordingJob(Path target) {
            return () -> {
                recorder.record(target, request.recordSeconds(), listener::progress);
                if (!Files.isRegularFile(target) || Files.size(target) == 0) {
                    throw new VideoExportException("The " + recorder.name() + " did not save a video.");
                }
                FfmpegTool tool = pipeline.ffmpeg().orElseThrow(() -> new VideoExportException(FfmpegTool.MISSING_MESSAGE));
                double seconds = tool.probeDuration(target);
                if (seconds < MIN_FOOTAGE_SECONDS) {
                    throw new VideoExportException("The recording is too short (" + seconds + " s).");
                }
                return () -> recordingFile = target;
            };
        }

        private Callable<Runnable> narrationJob(Path work, Path target) {
            return () -> {
                Narration made = pipeline.narrate(request.storyboard(), request.voice(), work, listener::progress);
                if (made.clips().isEmpty()) {
                    throw new NarrationException("The story has no narration text to speak.");
                }
                NarrationTrackWriter.write(made.storyboard(), made.clips(), target);
                return () -> {
                    narrated = made;
                    narrationFile = target;
                };
            };
        }

        private FlowResult finish(String stem) {
            return switch (request.mode()) {
                case RECORD_ONLY -> complete(null, List.of());
                case NARRATE_ONLY -> {
                    try {
                        writeScript(stem);
                    } catch (IOException ex) {
                        yield failed(new FlowFailure(FlowFailure.Kind.OUTPUT_FOLDER, "Could not write the script: " + ex.getMessage()));
                    }
                    yield complete(null, List.of());
                }
                case AUTO_EXPORT -> mux(stem);
            };
        }

        private FlowResult mux(String stem) {
            emit(FlowStage.MUXING);
            listener.progress(FlowStage.MUXING.label(language));
            ExportRequest export = new ExportRequest(request.storyboard(), request.context(), List.of(recordingFile), request.voice(),
                request.outputFolder(), request.baseName(), request.options(), false);
            try {
                ExportResult result = pipeline.export(export, narrated, stem, listener::progress);
                script = result.script();
                subtitles = result.subtitles();
                return complete(result.video(), result.warnings());
            } catch (VideoExportException ex) {
                if (Thread.currentThread().isInterrupted()) {
                    emit(FlowStage.CANCELLED);
                    return result(FlowStage.CANCELLED, null, null, List.of());
                }
                return failed(new FlowFailure(FlowFailure.Kind.MUX_FAILED, ex.getMessage()));
            }
        }

        private void writeScript(String stem) throws IOException {
            Storyboard story = narrated.storyboard();
            script = Files.writeString(request.outputFolder().resolve(stem + "-story.txt"), story.describe(), StandardCharsets.UTF_8);
            String srt = SubtitleWriter.toSrt(story);
            if (!srt.isBlank()) {
                subtitles = Files.writeString(request.outputFolder().resolve(stem + ".srt"), srt, StandardCharsets.UTF_8);
            }
        }

        private FlowResult complete(Path video, List<String> warnings) {
            emit(FlowStage.COMPLETE);
            FlowResult result = result(FlowStage.COMPLETE, null, video, warnings);
            listener.progress(FlowStage.COMPLETE.label(language) + ": " + result.output());
            return result;
        }

        private FlowResult failed(FlowFailure failure) {
            stage = FlowStage.FAILED;
            listener.status(new FlowStatus(stage, recording, narration, failure));
            listener.progress(FlowStage.FAILED.label(language) + failure.describe(language));
            return result(FlowStage.FAILED, failure, null, List.of());
        }

        private FlowResult result(FlowStage end, FlowFailure failure, Path video, List<String> warnings) {
            return new FlowResult(request.mode(), end, failure, video, recordingFile, narrationFile, script, subtitles, warnings);
        }

        private void cancelRunning(List<Future<Runnable>> futures) {
            futures.forEach(future -> future.cancel(true));
            if (recording == JobState.RUNNING) {
                recording = JobState.CANCELLED;
            }
            if (narration == JobState.RUNNING) {
                narration = JobState.CANCELLED;
            }
        }

        private void emit(FlowStage next) {
            stage = next;
            listener.status(new FlowStatus(stage, recording, narration, null));
        }
    }

    /** Stops the job threads and waits briefly so a cancelled recorder has really ended before its file is deleted. */
    private static void stopJobs(ExecutorService jobs) {
        jobs.shutdownNow();
        boolean interrupted = Thread.interrupted();
        try {
            jobs.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            interrupted = true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static final class JobThreads implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "video-flow-job-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
