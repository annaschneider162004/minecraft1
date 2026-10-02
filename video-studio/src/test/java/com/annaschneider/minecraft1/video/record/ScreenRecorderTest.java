package com.annaschneider.minecraft1.video.record;

import com.annaschneider.minecraft1.video.ProcessRunner;
import com.annaschneider.minecraft1.video.VideoExportException;
import com.annaschneider.minecraft1.video.render.FfmpegTool;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenRecorderTest {
    @Test
    void picksTheScreenGrabberOfTheOperatingSystem() {
        assertEquals(List.of("-f", "gdigrab", "-framerate", "30", "-i", "desktop"),
            ScreenRecorder.captureInput("Windows 11", name -> null).orElseThrow());
        assertEquals("avfoundation", ScreenRecorder.captureInput("Mac OS X", name -> null).orElseThrow().get(1));
        assertEquals(List.of("-f", "x11grab", "-framerate", "30", "-i", ":1"),
            ScreenRecorder.captureInput("Linux", Map.of("DISPLAY", ":1")::get).orElseThrow());
        assertTrue(ScreenRecorder.captureInput("Linux", name -> null).isEmpty(), "no X11 display: unsupported");
    }

    @Test
    void recordsForTheRequestedTimeWithFfmpeg() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        List<Duration> timeouts = new ArrayList<>();
        ProcessRunner runner = (command, stdin, timeout) -> {
            commands.add(command);
            timeouts.add(timeout);
            return new ProcessRunner.Result(0, "");
        };
        ScreenRecorder recorder = new ScreenRecorder(() -> Optional.of(new FfmpegTool(Path.of("ffmpeg"), runner)), "Windows 10",
            name -> null);
        assertTrue(recorder.unavailableReason().isEmpty());
        assertEquals("screen capture (gdigrab)", recorder.name());
        recorder.record(Path.of("out", "clip-recording.mp4"), 45, message -> { });

        List<String> command = commands.get(0);
        assertTrue(command.containsAll(List.of("gdigrab", "desktop", "libx264", "yuv420p")), command.toString());
        assertEquals("45.000", command.get(command.indexOf("-t") + 1));
        assertEquals(Path.of("out", "clip-recording.mp4").toString(), command.get(command.size() - 1));
        assertTrue(timeouts.get(0).toSeconds() > 45, "the process gets time to finish the file");
    }

    @Test
    void explainsWhyItCannotRecord() {
        ScreenRecorder noFfmpeg = new ScreenRecorder(Optional::empty, "Windows 10", name -> null);
        assertTrue(noFfmpeg.unavailableReason().orElseThrow().contains("FFmpeg was not found"));
        VideoExportException error = assertThrows(VideoExportException.class,
            () -> noFfmpeg.record(Path.of("x.mp4"), 5, message -> { }));
        assertTrue(error.getMessage().contains("FFmpeg"));
        ScreenRecorder noDisplay = new ScreenRecorder(Optional::empty, "Linux", name -> null);
        assertTrue(noDisplay.unavailableReason().orElseThrow().contains("DISPLAY"));
        assertTrue(noDisplay.unavailableReason().orElseThrow().contains("not available on Linux"));
    }
}
