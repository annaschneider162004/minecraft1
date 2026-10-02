package com.annaschneider.minecraft1.video.render;

import com.annaschneider.minecraft1.video.ExecutableLocator;
import com.annaschneider.minecraft1.video.ProcessRunner;
import com.annaschneider.minecraft1.video.VideoExportException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wrapper around the {@code ffmpeg} executable (optional dependency, found on demand). */
public final class FfmpegTool {
    public static final String ENV_VARIABLE = "ARCHITECT_FFMPEG";
    public static final String MISSING_MESSAGE = "FFmpeg was not found, so videos cannot be rendered. Install FFmpeg "
        + "(https://ffmpeg.org/download.html, e.g. 'winget install ffmpeg'), then add it to PATH, set "
        + ENV_VARIABLE + " or choose ffmpeg.exe in Video Studio > Tools.";
    private static final Pattern DURATION = Pattern.compile("Duration:\\s*(\\d+):(\\d{2}):(\\d{2}(?:\\.\\d+)?)");
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RENDER_TIMEOUT = Duration.ofMinutes(30);

    private final Path executable;
    private final ProcessRunner runner;

    public FfmpegTool(Path executable, ProcessRunner runner) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    /** Finds ffmpeg from the configured path, {@value #ENV_VARIABLE} or {@code PATH}. */
    public static Optional<FfmpegTool> locate(ExecutableLocator locator, String configured, List<Path> extraFolders,
                                              ProcessRunner runner) {
        return locator.find(configured, ENV_VARIABLE, "ffmpeg", extraFolders).map(path -> new FfmpegTool(path, runner));
    }

    public Path executable() {
        return executable;
    }

    /** Length of a video file in seconds. */
    public double probeDuration(Path file) throws VideoExportException {
        ProcessRunner.Result result = execute(List.of(executable.toString(), "-hide_banner", "-i", file.toString()), PROBE_TIMEOUT,
            "reading " + file.getFileName());
        Matcher matcher = DURATION.matcher(result.output());
        if (!matcher.find()) {
            throw new VideoExportException("Cannot read the length of " + file.getFileName()
                + " - is it a video file? " + result.tail(2));
        }
        return Integer.parseInt(matcher.group(1)) * 3600.0 + Integer.parseInt(matcher.group(2)) * 60.0
            + Double.parseDouble(matcher.group(3));
    }

    /** Runs ffmpeg with {@code arguments} (overwriting outputs, errors only) and fails with ffmpeg's last lines. */
    public void run(List<String> arguments, String step) throws VideoExportException {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(List.of("-hide_banner", "-nostdin", "-y", "-loglevel", "error"));
        command.addAll(arguments);
        ProcessRunner.Result result = execute(command, RENDER_TIMEOUT, step);
        if (!result.ok()) {
            throw new VideoExportException("FFmpeg failed while " + step + " (exit code " + result.exitCode() + "): "
                + result.tail(3));
        }
    }

    private ProcessRunner.Result execute(List<String> command, Duration timeout, String step) throws VideoExportException {
        try {
            return runner.run(command, null, timeout);
        } catch (IOException ex) {
            throw new VideoExportException("Could not run FFmpeg (" + executable + ") while " + step + ": " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new VideoExportException("Export cancelled while " + step, ex);
        }
    }
}
