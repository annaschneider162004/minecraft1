package com.annaschneider.minecraft1.video.record;

import com.annaschneider.minecraft1.video.VideoExportException;
import com.annaschneider.minecraft1.video.render.FfmpegTool;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Records the screen (with the Minecraft window on it) to an H.264 MP4 with FFmpeg's built-in screen grabbers:
 * {@code gdigrab} on Windows, {@code avfoundation} on macOS and {@code x11grab} on Linux/X11. No audio is captured.
 */
public final class ScreenRecorder implements FootageRecorder {
    static final int FPS = 30;
    private static final Duration STOP_MARGIN = Duration.ofMinutes(2);

    private final Supplier<Optional<FfmpegTool>> ffmpeg;
    private final Optional<List<String>> input;
    private final String grabber;

    public ScreenRecorder(Supplier<Optional<FfmpegTool>> ffmpeg) {
        this(ffmpeg, System.getProperty("os.name", ""), System::getenv);
    }

    ScreenRecorder(Supplier<Optional<FfmpegTool>> ffmpeg, String osName, Function<String, String> environment) {
        this.ffmpeg = ffmpeg;
        this.input = captureInput(osName, environment);
        this.grabber = input.map(args -> args.get(1)).orElse("none");
    }

    /** FFmpeg input arguments that grab the whole screen on this system, or empty when screen capture is unsupported. */
    static Optional<List<String>> captureInput(String osName, Function<String, String> environment) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String fps = String.valueOf(FPS);
        if (os.startsWith("windows")) {
            return Optional.of(List.of("-f", "gdigrab", "-framerate", fps, "-i", "desktop"));
        }
        if (os.startsWith("mac") || os.contains("darwin")) {
            return Optional.of(List.of("-f", "avfoundation", "-framerate", fps, "-capture_cursor", "1", "-i", "Capture screen 0:none"));
        }
        String display = environment.apply("DISPLAY");
        if (display == null || display.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(List.of("-f", "x11grab", "-framerate", fps, "-i", display.strip()));
    }

    @Override
    public String name() {
        return "screen capture (" + grabber + ")";
    }

    @Override
    public Optional<String> unavailableReason() {
        if (input.isEmpty()) {
            return Optional.of("Screen recording needs an X11 display (DISPLAY is not set). Record with OBS or ReplayMod "
                + "instead and use Export video.");
        }
        return ffmpeg.get().isPresent() ? Optional.empty() : Optional.of(FfmpegTool.MISSING_MESSAGE);
    }

    @Override
    public void record(Path output, double seconds, Consumer<String> progress) throws VideoExportException {
        Optional<String> problem = unavailableReason();
        if (problem.isPresent()) {
            throw new VideoExportException(problem.get());
        }
        FfmpegTool tool = ffmpeg.get().orElseThrow(() -> new VideoExportException(FfmpegTool.MISSING_MESSAGE));
        progress.accept(String.format(Locale.ROOT, "Recording the screen for %.0f s into %s...", seconds, output.getFileName()));
        tool.run(arguments(output, seconds), "recording the screen",
            Duration.ofMillis(Math.round(seconds * 1000)).plus(STOP_MARGIN));
    }

    List<String> arguments(Path output, double seconds) {
        List<String> args = new ArrayList<>(input.orElseThrow());
        // even dimensions are required by yuv420p H.264
        args.addAll(List.of("-t", String.format(Locale.ROOT, "%.3f", seconds), "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
            "-an", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23", "-pix_fmt", "yuv420p", output.toString()));
        return args;
    }
}
