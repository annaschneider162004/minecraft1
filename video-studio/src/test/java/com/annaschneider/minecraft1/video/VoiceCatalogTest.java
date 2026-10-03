package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.FfmpegTool;
import com.annaschneider.minecraft1.video.render.RenderOptions;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.voice.DownloadableVoice;
import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.Narrator;
import com.annaschneider.minecraft1.video.voice.PiperTtsEngine;
import com.annaschneider.minecraft1.video.voice.VoiceCatalog;
import com.annaschneider.minecraft1.video.voice.VoiceCatalogEntry;
import com.annaschneider.minecraft1.video.voice.VoiceCatalogSource;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoiceFilter;
import com.annaschneider.minecraft1.video.voice.VoiceInstallException;
import com.annaschneider.minecraft1.video.voice.VoiceInstaller;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.voice.VoicePackRegistry;
import com.annaschneider.minecraft1.video.voice.VoiceSelection;
import com.annaschneider.minecraft1.video.voice.VoiceSpeaker;
import com.annaschneider.minecraft1.video.voice.XttsTtsEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceCatalogTest {
    private static final String BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/main/";
    private static final String MULTI_CONFIG = "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"en_US\"},"
        + "\"num_speakers\":3,\"speaker_id_map\":{\"awb\":0,\"rms\":1,\"slt\":2}}";
    private static final String SINGLE_CONFIG = "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"en_US\"}}";

    @TempDir Path dir;

    // ------------------------------------------------------------------ bundled catalog

    @Test
    void bundledCatalogListsOnlyVerifiedEnglishAndVietnameseModelsWithChecksums() {
        VoiceCatalogSource.Parsed bundled = VoiceCatalogSource.bundled();
        assertTrue(bundled.problems().isEmpty(), bundled.problems().toString());
        assertEquals(41, bundled.models().size());
        for (DownloadableVoice model : bundled.models()) {
            assertTrue(model.language().startsWith("en_") || model.language().startsWith("vi_"), model.modelId());
            assertEquals(2, model.files().size());
            model.files().forEach(file -> {
                assertTrue(model.url(file).toString().startsWith(BASE), model.url(file).toString());
                assertTrue(file.md5().matches("[0-9a-f]{32}"));
            });
        }
        VoiceCatalog catalog = VoiceCatalog.build(new VoiceDiscovery(dir, List.of(), List.of()), bundled);
        // 41 real models; the 8 multi-speaker ones are expanded into their real speakers
        assertEquals(2073, catalog.size());
        assertEquals(41, catalog.modelCount());
        assertEquals(0, catalog.installedCount());
        assertEquals(2073, catalog.downloadableCount());
        assertEquals(67, catalog.filter(new VoiceFilter("", "vi", "", VoiceFilter.State.ALL, false)).size());
        assertEquals(65, catalog.filter(new VoiceFilter("vivos", "vi_VN", "piper", VoiceFilter.State.ALL, true)).size());
        assertEquals(1, catalog.filter(new VoiceFilter("vais1000", "", "", VoiceFilter.State.DOWNLOADABLE, false)).size());
        assertEquals(18, catalog.filter(new VoiceFilter("en_US-arctic-medium", "", "", VoiceFilter.State.ALL, false)).size());
        VoiceCatalogEntry awb = catalog.find("en_US-arctic-medium#speaker-0").orElseThrow();
        assertEquals("awb", awb.speaker().name());
        assertEquals(18, awb.speakerCount());
        assertTrue(awb.downloadable());
        assertEquals(List.of(awb), catalog.filter(new VoiceFilter("ARCTIC awb", "en", "", VoiceFilter.State.ALL, true)));
    }

    @Test
    void missingOrBrokenCatalogStillListsInstalledVoices() throws Exception {
        VoicePack pack = install("en_US-test-medium", SINGLE_CONFIG);
        for (VoiceCatalogSource.Parsed parsed : List.of(VoiceCatalogSource.Parsed.empty("The verified voice catalog is not available"),
            VoiceCatalogSource.parse("not json {"), VoiceCatalogSource.parse("{\"baseUrl\":\"http://example.com/\",\"models\":[]}"))) {
            VoiceCatalog catalog = VoiceCatalog.build(new VoiceDiscovery(dir, List.of(pack), List.of()), parsed);
            assertEquals(1, catalog.size());
            assertEquals(VoiceSelection.of(pack), catalog.select(pack.id()));
            assertFalse(catalog.problems().isEmpty());
        }
        assertTrue(VoiceCatalogSource.parse("{\"baseUrl\":\"http://huggingface.co/x/\",\"models\":[]}").problems().get(0)
            .contains("unsafe download address"));
    }

    @Test
    void invalidCatalogEntriesAreSkippedWithAReason() {
        String json = "{\"baseUrl\":\"" + BASE + "\",\"models\":["
            + model("en_US-good-medium", "en/en_US/good/medium/", null) + ","
            + model("en_US-good-medium", "en/en_US/good/medium/", null) + ","
            + model("en_US-escape-medium", "en/../../etc/", null) + ","
            + model("en_US-dup-medium", "en/x/", "[\"a\",\"a\"]") + ","
            + "{\"id\":\"en_US-nosum-low\",\"language\":\"en_US\",\"engine\":\"piper\",\"files\":["
            + "{\"path\":\"en/en_US-nosum-low.onnx\",\"size\":5,\"md5\":\"xyz\"},"
            + "{\"path\":\"en/en_US-nosum-low.onnx.json\",\"size\":5,\"md5\":\"" + "0".repeat(32) + "\"}]},"
            + "{\"id\":\"de_DE-other-low\",\"language\":\"de_DE\",\"engine\":\"coqui\",\"files\":[]}]}";
        VoiceCatalogSource.Parsed parsed = VoiceCatalogSource.parse(json);
        assertEquals(List.of("en_US-good-medium"), parsed.models().stream().map(DownloadableVoice::modelId).toList());
        String problems = String.join("\n", parsed.problems());
        assertEquals(5, parsed.problems().size(), problems);
        assertTrue(problems.contains("listed twice"), problems);
        assertTrue(problems.contains("invalid file path"), problems);
        assertTrue(problems.contains("duplicate speaker"), problems);
        assertTrue(problems.contains("invalid checksum"), problems);
        assertTrue(problems.contains("unsupported engine 'coqui'"), problems);
    }

    // ------------------------------------------------------------------ merge, expansion, ids

    @Test
    void mergesInstalledClonedAndDownloadableVoicesAndExpandsSpeakers() throws Exception {
        VoicePack single = install("en_US-amy-medium", SINGLE_CONFIG);
        VoicePack multi = install("en_US-arctic-medium", MULTI_CONFIG);
        VoicePack cloned = new VoicePack("clone-1", "Mine [cloned]", "en", XttsTtsEngine.ID, dir.resolve("s.wav"), null, 24000, "");
        String json = "{\"baseUrl\":\"" + BASE + "\",\"models\":["
            + model("en_US-amy-medium", "en/en_US/amy/medium/", null) + ","
            + model("en_US-arctic-medium", "en/en_US/arctic/medium/", "[\"awb\",\"rms\",\"slt\"]") + ","
            + model("vi_VN-vivos-x_low", "vi/vi_VN/vivos/x_low/", "[\"A\",\"B\"]") + "]}";
        VoiceCatalog catalog = VoiceCatalog.build(new VoiceDiscovery(dir, List.of(single, multi, cloned), List.of()),
            VoiceCatalogSource.parse(json));

        assertEquals(List.of("en_US-amy-medium", "en_US-arctic-medium#speaker-0", "en_US-arctic-medium#speaker-1",
            "en_US-arctic-medium#speaker-2", "clone-1", "vi_VN-vivos-x_low#speaker-0", "vi_VN-vivos-x_low#speaker-1"),
            catalog.entries().stream().map(VoiceCatalogEntry::id).toList());
        VoiceCatalogEntry amy = catalog.find("en_US-amy-medium").orElseThrow();
        assertTrue(amy.installed());
        assertFalse(amy.downloadable());
        assertNotNull(amy.download(), "installed and verified");
        assertEquals(VoiceSelection.of(single), catalog.select("en_US-amy-medium"));
        VoiceSelection slt = catalog.select("en_US-arctic-medium#speaker-2");
        assertEquals(multi, slt.voice());
        assertEquals(new VoiceSpeaker(2, "slt"), slt.speaker());
        assertTrue(slt.describe().contains("speaker 2 of en_US-arctic-medium"), slt.describe());
        assertEquals(VoiceSelection.of(cloned), catalog.select("clone-1"));
        assertTrue(catalog.find("clone-1").orElseThrow().cloned());

        assertEquals(List.of("en", "en_US", "vi_VN"), catalog.languages());
        assertEquals(List.of("piper", "xtts"), catalog.engines());
        assertEquals(5, catalog.filter(new VoiceFilter("", "", "", VoiceFilter.State.INSTALLED, false)).size());
        assertEquals(2, catalog.filter(new VoiceFilter("", "", "", VoiceFilter.State.DOWNLOADABLE, false)).size());
        assertEquals(1, catalog.filter(new VoiceFilter("", "", "", VoiceFilter.State.CLONED, false)).size());
        assertEquals(1, catalog.filter(new VoiceFilter("", "", XttsTtsEngine.ID, VoiceFilter.State.ALL, false)).size());
        assertEquals(5, catalog.filter(new VoiceFilter("", "", "", VoiceFilter.State.ALL, true)).size());
        assertEquals(4, catalog.filter(new VoiceFilter("", "en_US", "", VoiceFilter.State.ALL, false)).size());

        NarrationException notInstalled = assertThrows(NarrationException.class, () -> catalog.select("vi_VN-vivos-x_low#speaker-1"));
        assertTrue(notInstalled.getMessage().contains("not installed"), notInstalled.getMessage());
        assertTrue(assertThrows(NarrationException.class, () -> catalog.select("en_US-arctic-medium#speaker-9"))
            .getMessage().contains("not in the voice catalog"));
    }

    @Test
    void speakerIdsAreStableAcrossRefreshAndInstallAndIndependentOfNames() throws Exception {
        String json = "{\"baseUrl\":\"" + BASE + "\",\"models\":["
            + model("en_US-arctic-medium", "en/en_US/arctic/medium/", "[\"awb\",\"rms\",\"slt\"]") + "]}";
        VoiceCatalog before = VoiceCatalog.build(new VoiceDiscovery(dir, List.of(), List.of()), VoiceCatalogSource.parse(json));
        VoicePack installed = install("en_US-arctic-medium", MULTI_CONFIG.replace("\"rms\"", "\"renamed\""));
        VoiceCatalog after = VoiceCatalog.build(new VoiceDiscovery(dir, List.of(installed), List.of()), VoiceCatalogSource.parse(json));
        assertEquals(before.entries().stream().map(VoiceCatalogEntry::id).toList(),
            after.entries().stream().map(VoiceCatalogEntry::id).toList());
        assertEquals(VoiceSelection.speakerId("en_US-arctic-medium", 1), after.select("en_US-arctic-medium#speaker-1").id());
        assertEquals("renamed", after.select("en_US-arctic-medium#speaker-1").speaker().name());
        Set<String> ids = new HashSet<>();
        VoiceCatalog.build(new VoiceDiscovery(dir, List.of(), List.of()), VoiceCatalogSource.bundled()).entries()
            .forEach(entry -> assertTrue(ids.add(entry.id()), "duplicate id " + entry.id()));
        assertThrows(IllegalArgumentException.class, () -> new VoiceSelection("x", installed, null));
    }

    @Test
    void existingSinglePacksAndClonedProfilesKeepTheirIds() throws Exception {
        VoicePack pack = install("en_US-test-medium", SINGLE_CONFIG);
        VoiceDiscovery discovered = new VoicePackRegistry(Set.of("piper")).discover(dir);
        assertEquals(List.of(pack), discovered.voices());
        VoicePack cloned = new VoicePack("clone-2", "C [cloned]", "en", XttsTtsEngine.ID, dir.resolve("s.wav"), null, 24000, "");
        VoiceCatalog catalog = VoiceCatalog.build(new VoiceDiscovery(dir, List.of(pack, cloned), List.of()), null);
        assertEquals(List.of("en_US-test-medium", "clone-2"), catalog.entries().stream().map(VoiceCatalogEntry::id).toList());
        assertNull(catalog.select("en_US-test-medium").speaker());
        assertEquals(VoiceSelection.of(cloned), catalog.select("clone-2"));
    }

    @Test
    void filtersStayFastWithLargeCatalogs() {
        StringBuilder json = new StringBuilder("{\"baseUrl\":\"" + BASE + "\",\"models\":[");
        for (int m = 0; m < 500; m++) {
            StringBuilder speakers = new StringBuilder("[");
            for (int s = 0; s < 40; s++) {
                speakers.append(s == 0 ? "" : ",").append("\"spk").append(m).append('_').append(s).append('"');
            }
            json.append(m == 0 ? "" : ",").append(model((m % 2 == 0 ? "en_US" : "vi_VN") + "-synthetic" + m + "-low",
                "x/m" + m + "/", speakers + "]"));
        }
        VoiceCatalog catalog = VoiceCatalog.build(null, VoiceCatalogSource.parse(json + "]}"));
        assertEquals(20_000, catalog.size());
        long start = System.nanoTime();
        List<VoiceCatalogEntry> vi = catalog.filter(new VoiceFilter("", "vi", "", VoiceFilter.State.ALL, true));
        List<VoiceCatalogEntry> one = catalog.filter(new VoiceFilter("spk42_7", "", "piper", VoiceFilter.State.DOWNLOADABLE, false));
        List<VoiceCatalogEntry> none = catalog.filter(new VoiceFilter("", "", "", VoiceFilter.State.INSTALLED, false));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(10_000, vi.size());
        assertEquals(List.of("en_US-synthetic42-low#speaker-7"), one.stream().map(VoiceCatalogEntry::id).toList());
        assertTrue(none.isEmpty());
        assertTrue(millis < 2_000, "filtering 20,000 entries took " + millis + " ms");
    }

    // ------------------------------------------------------------------ preview / narration / export use the selection

    @Test
    void previewNarrationAndExportSpeakWithTheExactSelectedSpeaker() throws Exception {
        VoicePack multi = install("en_US-arctic-medium", MULTI_CONFIG);
        VoiceCatalog catalog = VoiceCatalog.build(new VoiceDiscovery(dir, List.of(multi), List.of()), null);
        VoiceSelection slt = catalog.select("en_US-arctic-medium#speaker-2");
        List<List<String>> commands = new ArrayList<>();
        Narrator narrator = new Narrator(List.of(piper(commands)));

        narrator.preview(slt, null, dir.resolve("preview.wav"));
        assertEquals("2", speakerOf(commands.get(0)));
        assertEquals(multi.model().toString(), commands.get(0).get(commands.get(0).indexOf("--model") + 1));

        Path ffmpegFile = Files.writeString(dir.resolve("ffmpeg"), "fake");
        VideoPipeline pipeline = new VideoPipeline(StoryService.templatesOnly(), narrator,
            () -> Optional.of(new FfmpegTool(ffmpegFile, new TestSupport.FakeFfmpeg())));
        Storyboard story = pipeline.writeStory(new StoryRequest("16 second tower build", null, null, "en")).storyboard();
        List<String> progress = new ArrayList<>();
        FlowResult narration = new ExportFlow(pipeline, null).run(new FlowRequest(ExportMode.NARRATE_ONLY, story, null, slt, 0,
            dir.resolve("out"), "narration", null), new FlowListener() {
                @Override public void status(FlowStatus status) { }
                @Override public void progress(String message) {
                    progress.add(message);
                }
            });
        assertTrue(narration.ok(), String.valueOf(narration.failure()));
        assertTrue(progress.contains("Voice: " + slt.describe()), progress.toString());
        int afterFlow = commands.size();
        assertTrue(afterFlow > 1);

        Path footage = Files.writeString(dir.resolve("footage.mp4"), "fake");
        List<String> exportProgress = new ArrayList<>();
        ExportResult export = pipeline.export(new ExportRequest(story, null, List.of(footage), slt, dir.resolve("out"),
            "video", RenderOptions.hd720(), false), exportProgress::add);
        assertTrue(export.narrated(), export.warnings().toString());
        assertTrue(commands.size() > afterFlow);
        assertTrue(exportProgress.contains("Generating narration with " + slt.describe() + "..."), exportProgress.toString());
        commands.forEach(command -> assertEquals("2", speakerOf(command), command.toString()));
    }

    @Test
    void invalidSpeakersAndUnsupportedEngineLanguageCombinationsFailClearly() throws Exception {
        VoicePack multi = install("en_US-arctic-medium", MULTI_CONFIG);
        VoicePack single = install("en_US-test-medium", SINGLE_CONFIG);
        List<List<String>> commands = new ArrayList<>();
        Narrator narrator = new Narrator(List.of(piper(commands)));

        VoiceSelection missing = VoiceSelection.of(multi, new VoiceSpeaker(7, "ghost"));
        NarrationException outOfRange = assertThrows(NarrationException.class,
            () -> narrator.preview(missing, "hi", dir.resolve("a.wav")));
        assertTrue(outOfRange.getMessage().contains("Speaker 7 does not exist"), outOfRange.getMessage());
        assertTrue(outOfRange.getMessage().contains("3 speakers"), outOfRange.getMessage());

        VoiceSelection onSingle = VoiceSelection.of(single, new VoiceSpeaker(1, "x"));
        assertTrue(narrator.unavailableReason(onSingle).orElseThrow().contains("single speaker"));
        assertTrue(commands.isEmpty(), "never falls back to another voice or speaker");

        TestSupport.FakeTts noSpeakers = new TestSupport.FakeTts();
        VoiceSelection speaker = VoiceSelection.of(multi, new VoiceSpeaker(1, "rms"));
        assertTrue(new Narrator(List.of(noSpeakers)).unavailableReason(speaker).orElseThrow().contains("cannot choose a speaker"));
        assertThrows(NarrationException.class, () -> new Narrator(List.of(noSpeakers)).preview(speaker, "hi", dir.resolve("b.wav")));
        assertTrue(noSpeakers.spoken.isEmpty());

        Path exe = Files.writeString(dir.resolve("tts"), "");
        XttsTtsEngine xtts = new XttsTtsEngine(exe, dir, (command, stdin, timeout) -> { throw new AssertionError("must not run"); });
        VoicePack vietnameseClone = new VoicePack("clone-vi", "Vi", "vi", XttsTtsEngine.ID, dir.resolve("s.wav"), null, 24000, "");
        assertTrue(xtts.unsupportedReason(VoiceSelection.of(vietnameseClone)).orElseThrow().contains("English profiles only"));
        assertTrue(new Narrator(List.of()).unavailableReason(VoiceSelection.of(multi)).orElseThrow().contains("'piper' engine"));
    }

    // ------------------------------------------------------------------ explicit install

    @Test
    void installsAVerifiedVoiceAtomicallyAndListsItsSpeakers() throws Exception {
        byte[] model = "fake onnx model".getBytes(StandardCharsets.UTF_8);
        byte[] config = MULTI_CONFIG.getBytes(StandardCharsets.UTF_8);
        DownloadableVoice voice = downloadable("en_US-arctic-medium", model, config, List.of("awb", "rms", "slt"));
        Map<URI, byte[]> served = new HashMap<>();
        served.put(voice.url(voice.files().get(0)), model);
        served.put(voice.url(voice.files().get(1)), config);
        Path voices = dir.resolve("voices");
        List<String> progress = new ArrayList<>();
        Path target = new VoiceInstaller(url -> new ByteArrayInputStream(served.get(url))).install(voice, voices, progress::add);

        assertEquals(voices.resolve("en_US-arctic-medium"), target);
        assertNoStaging(voices);
        VoiceDiscovery found = new VoicePackRegistry(Set.of("piper")).discover(voices);
        assertTrue(found.problems().isEmpty(), found.problems().toString());
        VoiceCatalog catalog = VoiceCatalog.build(found, null);
        assertEquals(3, catalog.size());
        assertEquals("slt", catalog.select("en_US-arctic-medium#speaker-2").speaker().name());
        assertTrue(progress.get(progress.size() - 1).startsWith("Installed en_US-arctic-medium"));

        VoiceInstallException again = assertThrows(VoiceInstallException.class,
            () -> new VoiceInstaller(url -> new ByteArrayInputStream(served.get(url))).install(voice, voices, message -> { }));
        assertTrue(again.getMessage().contains("already in the voices folder"));
    }

    @Test
    void failedDownloadsAreRolledBack() throws Exception {
        byte[] model = "fake onnx model".getBytes(StandardCharsets.UTF_8);
        byte[] config = MULTI_CONFIG.getBytes(StandardCharsets.UTF_8);
        Path voices = dir.resolve("voices");

        DownloadableVoice good = downloadable("en_US-arctic-medium", model, config, List.of("awb", "rms", "slt"));
        assertRolledBack(good, voices, url -> new ByteArrayInputStream(url.toString().endsWith(".json") ? config
            : "fake onnx MODEL".getBytes(StandardCharsets.UTF_8)), "checksum");
        assertRolledBack(good, voices, url -> new ByteArrayInputStream(url.toString().endsWith(".json") ? config
            : "fake".getBytes(StandardCharsets.UTF_8)), "incomplete");
        assertRolledBack(good, voices, url -> new ByteArrayInputStream(url.toString().endsWith(".json") ? config
            : "fake onnx model and more".getBytes(StandardCharsets.UTF_8)), "larger than the verified size");
        assertRolledBack(good, voices, url -> {
            if (url.toString().endsWith(".json")) {
                throw new java.io.IOException("connection reset");
            }
            return new ByteArrayInputStream(model);
        }, "connection reset");

        DownloadableVoice wrongSpeakers = downloadable("en_US-arctic-medium", model, config, List.of("awb", "rms"));
        assertRolledBack(wrongSpeakers, voices, url -> new ByteArrayInputStream(url.toString().endsWith(".json") ? config : model),
            "speaker");

        byte[] badConfig = "{\"audio\":{}}".getBytes(StandardCharsets.UTF_8);
        DownloadableVoice invalid = downloadable("en_US-arctic-medium", model, badConfig, List.of());
        assertRolledBack(invalid, voices, url -> new ByteArrayInputStream(url.toString().endsWith(".json") ? badConfig : model),
            "not valid");

        DownloadableVoice unsafe = new DownloadableVoice("en_US-x-low", "x", "en_US", "piper", "low", good.files(), List.of(),
            URI.create("http://example.com/"));
        assertRolledBack(unsafe, voices, url -> { throw new AssertionError("must not download"); }, "unverified address");
    }

    @Test
    void registryIgnoresUnfinishedInstalls() throws Exception {
        Path staging = Files.createDirectories(dir.resolve(VoiceInstaller.STAGING_PREFIX + "123"));
        Files.write(staging.resolve("en_US-half-medium.onnx"), new byte[] {1});
        Files.writeString(staging.resolve("en_US-half-medium.onnx.json"), SINGLE_CONFIG);
        VoiceDiscovery found = new VoicePackRegistry(Set.of("piper")).discover(dir);
        assertTrue(found.voices().isEmpty());
        assertTrue(found.problems().isEmpty(), found.problems().toString());
    }

    // ------------------------------------------------------------------ helpers

    private void assertRolledBack(DownloadableVoice voice, Path voices, VoiceInstaller.Downloader downloader, String reason)
        throws Exception {
        VoiceInstallException error = assertThrows(VoiceInstallException.class,
            () -> new VoiceInstaller(downloader).install(voice, voices, message -> { }));
        assertTrue(error.getMessage().contains(reason), error.getMessage());
        assertFalse(Files.exists(voices.resolve(voice.modelId())), "nothing is installed");
        assertNoStaging(voices);
    }

    private static void assertNoStaging(Path voices) throws Exception {
        if (!Files.isDirectory(voices)) {
            return;
        }
        try (Stream<Path> files = Files.list(voices)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(VoiceInstaller.STAGING_PREFIX)));
        }
    }

    private static DownloadableVoice downloadable(String id, byte[] model, byte[] config, List<String> speakers) throws Exception {
        List<VoiceSpeaker> list = new ArrayList<>();
        for (int i = 0; i < speakers.size(); i++) {
            list.add(new VoiceSpeaker(i, speakers.get(i)));
        }
        return new DownloadableVoice(id, "arctic", "en_US", "piper", "medium", List.of(
            new DownloadableVoice.CatalogFile("en/en_US/arctic/medium/" + id + ".onnx", model.length, md5(model)),
            new DownloadableVoice.CatalogFile("en/en_US/arctic/medium/" + id + ".onnx.json", config.length, md5(config))),
            list, URI.create(BASE));
    }

    private static String md5(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(data));
    }

    private VoicePack install(String id, String config) throws Exception {
        Files.write(dir.resolve(id + ".onnx"), new byte[] {1});
        Files.writeString(dir.resolve(id + ".onnx.json"), config);
        return new VoicePackRegistry(Set.of("piper")).load(dir, dir.resolve(id + ".onnx"));
    }

    private PiperTtsEngine piper(List<List<String>> commands) throws Exception {
        Path exe = Files.writeString(dir.resolve("piper"), "");
        return new PiperTtsEngine(exe, (command, stdin, timeout) -> {
            commands.add(command);
            TestSupport.writeWav(Path.of(command.get(command.size() - 1)), 1);
            return new ProcessRunner.Result(0, "");
        });
    }

    private static String speakerOf(List<String> command) {
        int at = command.indexOf("--speaker");
        return at < 0 ? null : command.get(at + 1);
    }

    private static String model(String id, String folder, String speakers) {
        String sum = "0".repeat(32);
        return "{\"id\":\"" + id + "\",\"name\":\"n\",\"language\":\"" + id.substring(0, 5) + "\",\"engine\":\"piper\","
            + "\"quality\":\"low\",\"files\":[{\"path\":\"" + folder + id + ".onnx\",\"size\":10,\"md5\":\"" + sum + "\"},"
            + "{\"path\":\"" + folder + id + ".onnx.json\",\"size\":10,\"md5\":\"" + sum + "\"}]"
            + (speakers == null ? "" : ",\"speakers\":" + speakers) + "}";
    }
}
