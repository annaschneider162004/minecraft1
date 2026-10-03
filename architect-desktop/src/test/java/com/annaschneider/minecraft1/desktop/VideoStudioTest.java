package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.JobStatus;
import com.annaschneider.minecraft1.link.PlanSummary;
import com.annaschneider.minecraft1.link.RegionBox;
import com.annaschneider.minecraft1.video.BuildContext;
import com.annaschneider.minecraft1.video.BuildMilestone;
import com.annaschneider.minecraft1.video.ExecutableLocator;
import com.annaschneider.minecraft1.video.ExportMode;
import com.annaschneider.minecraft1.video.ProcessRunner;
import com.annaschneider.minecraft1.video.voice.VoiceCatalogEntry;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoiceFilter;
import com.annaschneider.minecraft1.video.voice.NarrationAudioOptions;
import com.annaschneider.minecraft1.video.voice.VoiceInstallException;
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
import javax.swing.JList;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoStudioTest {
    @Test
    void restoresClonedSelectionAndFiltersTheUnifiedCatalog(@TempDir Path dir) throws Exception {
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
            settle(window.get());
            JList<?> voices = (JList<?>) field(window.get(), "voiceList");
            JComboBox<?> states = (JComboBox<?>) field(window.get(), "stateBox");
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(id, ((VoiceCatalogEntry) voices.getSelectedValue()).id());
                assertEquals(id, settings.voiceId(), "refresh must not overwrite the saved selection");
                assertTrue(listed(voices).contains("en_US-test-medium"), "installed packs are listed with the clones");
                assertTrue(listed(voices).contains("en_US-arctic-medium#speaker-0"), "verified downloadable voices are listed");
                states.setSelectedItem(VoiceFilter.State.INSTALLED);
            });
            settle(window.get());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(List.of("en_US-test-medium", id), listed(voices));
                assertEquals(id, ((VoiceCatalogEntry) voices.getSelectedValue()).id(), "filters keep the selection");
                voices.setSelectedIndex(1);
                assertEquals("en_US-test-medium", settings.voiceId());
                states.setSelectedItem(VoiceFilter.State.CLONED);
            });
            settle(window.get());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(List.of(id), listed(voices));
                assertNull(voices.getSelectedValue(), "the chosen pack is hidden, not replaced");
                assertEquals("en_US-test-medium", settings.voiceId());
                voices.setSelectedIndex(1);
                assertEquals(id, settings.voiceId());
                voices.setSelectedIndex(0);
                assertEquals("", settings.voiceId());
                invoke(window.get(), "refreshVoices");
            });
            settle(window.get());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("", settings.voiceId(), "refresh preserves explicit no narration");
                assertEquals(0, voices.getSelectedIndex());
                assertEquals("Nghe thử giọng", ((javax.swing.JButton) field(window.get(), "previewButton")).getText());
                ((javax.swing.JSpinner) field(window.get(), "speedSpinner")).setValue(1.5);
                ((JComboBox<?>) field(window.get(), "effectBox")).setSelectedItem(NarrationAudioOptions.Effect.SOFT_ECHO);
                assertEquals(1.5, settings.narrationSpeed());
                assertEquals(NarrationAudioOptions.Effect.SOFT_ECHO, settings.narrationEffect());
            });
        } finally {
            if (window.get() != null) {
                SwingUtilities.invokeAndWait(window.get()::dispose);
                ((ExecutorService) field(window.get(), "worker")).shutdownNow();
            }
            node.removeNode();
        }
    }

    private static void settle(VideoStudioWindow window) throws Exception {
        for (String executor : List.of("worker", "filterWorker", "worker", "filterWorker")) {
            ((ExecutorService) field(window, executor)).submit(() -> { }).get(10, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    private static List<String> listed(JList<?> list) {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (list.getModel().getElementAt(i) instanceof VoiceCatalogEntry entry) {
                ids.add(entry.id());
            }
        }
        return ids;
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

    private static void invoke(VideoStudioWindow window, String name) {
        try {
            var method = VideoStudioWindow.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(window);
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
            assertEquals(NarrationAudioOptions.defaults(), settings.narrationAudioOptions());
            settings.setExportMode(ExportMode.NARRATE_ONLY);
            settings.setRecordSeconds(1_000_000);
            settings.setFfmpegPath("  C:/ffmpeg/bin/ffmpeg.exe ");
            settings.setVoicesFolder("/data/voices");
            settings.setVoiceId("vi_VN-vais1000-medium");
            settings.setCloningPath(" /data/tools/tts ");
            settings.setCloningModelFolder("/data/models/xtts-v2");
            settings.setUseLocalAi(true);
            settings.setNarrationSpeed(1.7);
            settings.setNarrationEffect(NarrationAudioOptions.Effect.SOFT_ECHO);
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
            assertEquals(new NarrationAudioOptions(1.7, NarrationAudioOptions.Effect.SOFT_ECHO), reopened.narrationAudioOptions());
            node.put("exportMode", "BOGUS");
            assertEquals(ExportMode.AUTO_EXPORT, reopened.exportMode());
            node.putDouble("narrationSpeed", Double.NaN);
            node.put("narrationEffect", "BOGUS");
            assertEquals(NarrationAudioOptions.defaults(), reopened.narrationAudioOptions());
            reopened.setNarrationSpeed(0.1);
            assertEquals(0.5, reopened.narrationSpeed());
            reopened.setNarrationSpeed(8);
            assertEquals(2.0, reopened.narrationSpeed());
            reopened.setNarrationSpeed(Double.POSITIVE_INFINITY);
            assertEquals(1.0, reopened.narrationSpeed());
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
            assertTrue(report.contains("FFmpeg: KHÔNG TÌM THẤY"), report);
            assertTrue(report.contains("Bộ đọc Piper: KHÔNG TÌM THẤY"), report);
            assertTrue(report.contains("Nhân bản giọng chưa sẵn sàng"), report);
            assertTrue(report.contains("lonely.onnx: missing config file"), report);
            assertTrue(report.contains("mẫu có sẵn"), report);
            assertTrue(report.contains("Ghi màn hình: CHƯA SẴN SÀNG"), report);
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
        assertTrue(VideoStudioWindow.helpHtml().contains("thư mục giọng"));
        for (ExportMode mode : ExportMode.values()) {
            assertTrue(VideoStudioWindow.helpHtml().contains(DesktopVoiceSupport.label(mode)), mode.label());
        }
        assertTrue(VideoStudioWindow.helpHtml().contains("Chờ tác vụ còn lại"));
        assertTrue(VideoStudioWindow.helpHtml().contains("Nhân bản giọng từ WAV"));
        assertTrue(VideoStudioWindow.helpHtml().contains("không hỗ trợ nhân bản tiếng Việt"));
        assertTrue(VideoStudioWindow.helpHtml().contains("0,5–2,0×"));
    }

    @Test
    void controllerImportsLocalPackWithoutChangingVoiceSelection(@TempDir Path dir) throws Exception {
        Preferences node = Preferences.userRoot().node("architect-video-import-test-" + UUID.randomUUID());
        try {
            Path source = Files.createDirectory(dir.resolve("source"));
            Path model = Files.write(source.resolve("vi_VN-local.onnx"), new byte[] {8, 1, 2});
            Path config = Files.writeString(source.resolve("vi_VN-local.onnx.json"),
                "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"vi_VN\"}}");
            VideoStudioSettings settings = new VideoStudioSettings(node);
            settings.setVoicesFolder(dir.resolve("voices").toString());
            settings.setVoiceId("existing-selection");
            ProcessRunner never = (command, stdin, timeout) -> { throw new AssertionError("import must not run a backend"); };
            VideoStudio studio = new VideoStudio(settings, new ExecutableLocator("", false, name -> null), never);
            var imported = studio.importVoicePack(model, config, "Giọng của tôi", "Thuyết minh công trình");
            assertEquals("Giọng của tôi", imported.name());
            assertEquals("existing-selection", settings.voiceId());
            var discovered = studio.discoverVoices();
            assertEquals(1, discovered.voices().size());
            assertTrue(studio.voiceCatalog(discovered).find(imported.id()).isPresent());
            assertThrows(VoiceInstallException.class, () -> studio.importVoicePack(model, config, "", ""));
            assertEquals("existing-selection", settings.voiceId());
            assertEquals(1, studio.discoverVoices().voices().size(), "failed import preserves installed pack");
        } finally {
            node.removeNode();
        }
    }

    private static JobStatus job(long id, String state, double percent, long blocks) {
        return new JobStatus(id, "white-palace", "build", state, percent, 0, 0, blocks, false, null);
    }
}
