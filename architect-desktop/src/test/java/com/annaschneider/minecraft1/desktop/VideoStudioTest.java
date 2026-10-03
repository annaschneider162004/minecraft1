package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.JobStatus;
import com.annaschneider.minecraft1.link.PlanSummary;
import com.annaschneider.minecraft1.link.RegionBox;
import com.annaschneider.minecraft1.video.BuildContext;
import com.annaschneider.minecraft1.video.BuildMilestone;
import com.annaschneider.minecraft1.video.ExecutableLocator;
import com.annaschneider.minecraft1.video.ExportMode;
import com.annaschneider.minecraft1.video.ProcessRunner;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoStudioTest {
    @Test
    void restoresClonedSelectionAndSwitchesBetweenProfilesAndPacks(@TempDir Path dir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "needs a display");
        String id = "clone-" + UUID.randomUUID();
        Path sample = dir.resolve(id + ".sample.wav");
        var format = new javax.sound.sampled.AudioFormat(22050, 16, 1, true, false);
        try (var audio = new javax.sound.sampled.AudioInputStream(
            new java.io.ByteArrayInputStream(new byte[22050 * 6 * 2]), format, 22050 * 6)) {
            javax.sound.sampled.AudioSystem.write(audio, javax.sound.sampled.AudioFileFormat.Type.WAVE, sample.toFile());
        }
        Files.writeString(dir.resolve(id + ".cloned.json"), "{\"name\":\"Saved voice\",\"engine\":\"xtts\",\"language\":\"en\"}");
        Files.write(dir.resolve("en_US-test-medium.onnx"), new byte[] {1});
        Files.writeString(dir.resolve("en_US-test-medium.onnx.json"),
            "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"en_US\"}}");
        Preferences node = Preferences.userRoot().node("architect-video-ui-test-" + UUID.randomUUID());
        AtomicReference<VideoStudioWindow> window = new AtomicReference<>();
        try {
            VideoStudioSettings settings = new VideoStudioSettings(node);
            settings.setVoicesFolder(dir.toString());
            settings.setVoiceId(id);
            ProcessRunner never = (command, stdin, timeout) -> { throw new AssertionError("no backend should run"); };
            VideoStudio studio = new VideoStudio(settings, new ExecutableLocator("", false, name -> null), never);
            assertEquals(2, studio.discoverVoices().voices().size());
            SwingUtilities.invokeAndWait(() -> {
                window.set(new VideoStudioWindow(studio, BuildContext::empty, () -> "No build", dir));
                window.get().showStudio();
            });
            ExecutorService worker = (ExecutorService) field(window.get(), "worker");
            worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> modes = (JComboBox<?>) field(window.get(), "voiceMode");
                JComboBox<?> voices = (JComboBox<?>) field(window.get(), "voiceBox");
                assertEquals(2, modes.getSelectedIndex());
                assertEquals(id, ((VoicePack) voices.getSelectedItem()).id());
                assertEquals(id, settings.voiceId(), "refresh must not overwrite the saved selection");
                modes.setSelectedIndex(0);
                assertEquals(VoiceSelection.NONE, voices.getSelectedItem());
                assertEquals(id, settings.voiceId(), "switching modes must not silently choose a different speaker");
                modes.setSelectedIndex(1);
                assertEquals(VoiceSelection.NONE, voices.getSelectedItem());
                modes.setSelectedIndex(2);
                assertEquals(id, ((VoicePack) voices.getSelectedItem()).id());
            });
        } finally {
            if (window.get() != null) {
                SwingUtilities.invokeAndWait(window.get()::dispose);
                ((ExecutorService) field(window.get(), "worker")).shutdownNow();
            }
            node.removeNode();
        }
    }

    private static Object field(VideoStudioWindow window, String name) {
        try {
            var field = VideoStudioWindow.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(window);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void recordsMilestonesRelativeToTheRecordingStart() {
        AtomicLong now = new AtomicLong(1_000_000);
        BuildTimelineRecorder timeline = new BuildTimelineRecorder(now::get);
        assertFalse(timeline.hasBuild());
        timeline.plan(new PlanSummary("white-palace", "t", "x", 10, 10, 10, 5000,
            List.of(new RegionBox("PALACE", 0, 0, 1, 1), new RegionBox("water_fall", 0, 0, 1, 1), new RegionBox("PALACE", 0, 0, 1, 1))));
        timeline.recordingStarted();
        now.addAndGet(4_000);
        timeline.onJob(job(7, "queued", 0, 0));
        timeline.onJob(job(7, "running", 2, 10));
        now.addAndGet(10_000);
        timeline.onJob(job(7, "running", 8, 100));
        timeline.onJob(job(7, "running", 12, 150));
        timeline.onJob(job(7, "paused", 12, 150));
        now.addAndGet(20_000);
        timeline.onJob(job(7, "running", 55, 700));
        now.addAndGet(6_000);
        timeline.onJob(job(7, "completed", 100, 1234));
        timeline.onJob(new JobStatus(8, "white-palace", "undo", "running", 50, 1, 2, 3, false, null));

        BuildContext context = timeline.snapshot();
        assertTrue(timeline.hasBuild());
        assertEquals("white-palace", context.buildName());
        assertEquals(List.of("palace", "water fall"), context.sections());
        assertEquals(1234, context.blocks());
        assertEquals(List.of(new BuildMilestone(4, 2, "started"), new BuildMilestone(14, 10, "10%"),
            new BuildMilestone(34, 50, "50%"), new BuildMilestone(40, 100, "completed")),
            context.milestones());
        assertTrue(timeline.describe().contains("4 milestones"));

        // a later build without recording starts its own timeline at the build start
        timeline.recordingStopped();
        now.addAndGet(60_000);
        timeline.onJob(job(9, "running", 0, 0));
        assertEquals(List.of(new BuildMilestone(0, 0, "started")), timeline.snapshot().milestones());
    }

    @Test
    void settingsRoundTripWithSafeDefaults() throws Exception {
        Preferences node = Preferences.userRoot().node("architect-video-test-" + UUID.randomUUID());
        try {
            VideoStudioSettings settings = new VideoStudioSettings(node);
            assertEquals("", settings.ffmpegPath());
            assertEquals("", settings.cloningPath());
            assertEquals("", settings.cloningModelFolder());
            assertFalse(settings.useLocalAi());
            assertEquals("http://127.0.0.1:11434", settings.ollamaUrl());
            assertEquals(VideoStudioSettings.defaultOutputFolder(), settings.outputFolder());
            assertEquals(ExportMode.AUTO_EXPORT, settings.exportMode());
            assertEquals(VideoStudioSettings.DEFAULT_RECORD_SECONDS, settings.recordSeconds());
            settings.setExportMode(ExportMode.NARRATE_ONLY);
            settings.setRecordSeconds(1_000_000);
            settings.setFfmpegPath("  C:/ffmpeg/bin/ffmpeg.exe ");
            settings.setVoicesFolder("/data/voices");
            settings.setVoiceId("vi_VN-vais1000-medium");
            settings.setCloningPath(" /data/tools/tts ");
            settings.setCloningModelFolder("/data/models/xtts-v2");
            settings.setUseLocalAi(true);
            settings.setOutputFolder("");
            node.flush();
            VideoStudioSettings reopened = new VideoStudioSettings(Preferences.userRoot().node(node.absolutePath()));
            assertEquals("C:/ffmpeg/bin/ffmpeg.exe", reopened.ffmpegPath());
            assertEquals(Path.of("/data/voices"), reopened.voicesFolder());
            assertEquals("vi_VN-vais1000-medium", reopened.voiceId());
            assertEquals("/data/tools/tts", reopened.cloningPath());
            assertEquals("/data/models/xtts-v2", reopened.cloningModelFolder());
            assertTrue(reopened.useLocalAi());
            assertEquals(VideoStudioSettings.defaultOutputFolder(), reopened.outputFolder());
            assertEquals(ExportMode.NARRATE_ONLY, reopened.exportMode());
            assertEquals(3600, reopened.recordSeconds());
            node.put("exportMode", "BOGUS");
            assertEquals(ExportMode.AUTO_EXPORT, reopened.exportMode());
        } finally {
            node.removeNode();
        }
    }

    @Test
    void diagnosticsExplainMissingToolsAndInvalidVoices(@TempDir Path dir) throws Exception {
        Preferences node = Preferences.userRoot().node("architect-video-test-" + UUID.randomUUID());
        try {
            VideoStudioSettings settings = new VideoStudioSettings(node);
            Path voices = Files.createDirectories(dir.resolve("voices"));
            Files.write(voices.resolve("lonely.onnx"), new byte[] {1});
            settings.setVoicesFolder(voices.toString());
            settings.setFfmpegPath(dir.resolve("missing-ffmpeg.exe").toString());
            settings.setPiperPath(dir.resolve("missing-piper.exe").toString());
            ProcessRunner never = (command, stdin, timeout) -> {
                throw new AssertionError("no program should run");
            };
            VideoStudio studio = new VideoStudio(settings, new ExecutableLocator("", false, name -> null), never);
            VoiceDiscovery found = studio.discoverVoices();
            assertTrue(found.voices().isEmpty());
            String report = String.join("\n", studio.diagnostics(found));
            assertTrue(report.contains("FFmpeg: NOT FOUND"), report);
            assertTrue(report.contains("Piper voice engine: NOT FOUND"), report);
            assertTrue(report.contains("Voice cloning unavailable"), report);
            assertTrue(report.contains("lonely.onnx: missing config file"), report);
            assertTrue(report.contains("built-in templates"), report);
            assertTrue(report.contains("Screen recording: NOT AVAILABLE"), report);
            assertTrue(studio.flow() != null);
            assertTrue(studio.narrator().supportedEngines().contains("piper"));
        } finally {
            node.removeNode();
        }
    }

    @Test
    void replayVideosFolderSitsNextToTheGameConfig() {
        Path link = Path.of("/games/.minecraft/config/architect/desktop-link.json");
        assertEquals(Path.of("/games/.minecraft/replay_videos"), VideoStudio.replayVideosFolder(link));
        assertTrue(VideoStudioWindow.helpHtml().contains("voices folder"));
        for (ExportMode mode : ExportMode.values()) {
            assertTrue(VideoStudioWindow.helpHtml().contains(mode.label()), mode.label());
        }
        assertTrue(VideoStudioWindow.helpHtml().contains("Waiting for remaining job..."));
        assertTrue(VideoStudioWindow.helpHtml().contains("Clone voice from sample..."));
    }

    private static JobStatus job(long id, String state, double percent, long blocks) {
        return new JobStatus(id, "white-palace", "build", state, percent, 0, 0, blocks, false, null);
    }
}
