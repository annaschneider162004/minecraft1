package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.VoiceCatalog;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VoiceCatalogTest {
    @Test
    void filtersOneThousandSyntheticTestOnlySpeakersWithoutModelLoading() {
        // Synthetic TEST ONLY identities; never shipped as voice inventory.
        var speakers = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < 1200; i++) {
            speakers.put("TEST_ONLY_" + i, i);
        }
        var model = new VoiceCatalog.Model("en_US-test_only-medium", "TEST ONLY", "en_US", 1200, speakers, 22050,
            URI.create("https://huggingface.co/rhasspy/piper-voices"), "TEST ONLY", "", false, List.of());
        var catalog = new VoiceCatalog(List.of(model));
        var entries = catalog.entries(new VoiceDiscovery(Path.of("/unused"), List.of(), List.of()));
        assertEquals(1200, entries.size());
        assertEquals("en_US-test_only-medium", entries.get(0).id());
        assertEquals("en_US-test_only-medium-speaker-1199", entries.get(1199).id());
        assertTrue(entries.stream().allMatch(e -> e.state() == VoiceCatalog.State.UNAVAILABLE && !e.installed()));
        assertEquals(1200, VoiceCatalog.filter(entries, "en", "", false, Set.of()).size());
        assertTrue(VoiceCatalog.filter(entries, "vi", "", false, Set.of()).isEmpty());
        assertEquals(1, VoiceCatalog.filter(entries, "en", "TEST_ONLY_1199", true,
            Set.of(entries.get(1199).id())).size());
        assertEquals(entries, catalog.entries(new VoiceDiscovery(Path.of("/unused"), List.of(), List.of())));
    }

    @Test
    void invalidSpeakerMapsAndDuplicateModelsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> model(java.util.Map.of("a", 0, "b", 0)));
        assertThrows(IllegalArgumentException.class, () -> model(java.util.Map.of("a", 0, "b", 2)));
        assertThrows(IllegalArgumentException.class, () -> model(java.util.Map.of("a", 0)));
        var valid = model(java.util.Map.of("a", 0, "b", 1));
        assertThrows(IllegalArgumentException.class, () -> new VoiceCatalog(List.of(valid, valid)));
    }

    @Test
    void installedSpeakersMatchIdentityAndClonesStaySeparate() {
        var model = model(java.util.Map.of("a", 0, "b", 1));
        var catalog = new VoiceCatalog(List.of(model));
        Path path = Path.of("/unused");
        VoicePack installed = new VoicePack(model.voiceId(1), "b", "en_US", "piper", path, path, 22050, "", 1, "b");
        VoicePack cloned = new VoicePack("clone-test", "Local clone", "en", "xtts", path, path, 24000, "");
        var entries = catalog.entries(new VoiceDiscovery(path, List.of(installed, cloned), List.of()));
        assertEquals(3, entries.size());
        assertEquals(VoiceCatalog.State.UNAVAILABLE, entries.get(0).state());
        assertEquals(installed, entries.get(1).installedVoice());
        assertEquals("Cloned profile", entries.get(2).source());
        VoicePack wrong = new VoicePack(model.voiceId(1), "wrong", "en_US", "piper", path, path, 22050, "", 1, "wrong");
        var mismatch = catalog.entries(new VoiceDiscovery(path, List.of(wrong), List.of()));
        assertEquals(2, mismatch.size());
        assertEquals("Local Piper", mismatch.get(1).source());
        assertEquals(wrong, mismatch.get(1).installedVoice());
    }

    @Test
    void bundledInventoryHasNoQualityVariantsOrFakeSpeakers() throws Exception {
        VoiceCatalog catalog = VoiceCatalog.bundled();
        assertFalse(catalog.models().isEmpty());
        var ids = new java.util.HashSet<String>();
        for (var entry : catalog.entries(new VoiceDiscovery(Path.of("/unused"), List.of(), List.of()))) {
            assertTrue(ids.add(entry.id()), entry.id());
            assertFalse(entry.installed());
            assertNotEquals(VoiceCatalog.State.INSTALLED, entry.state());
        }
        var vais = catalog.models().stream().filter(m -> m.id().equals("vi_VN-vais1000-medium")).findFirst().orElseThrow();
        assertEquals(1, vais.numSpeakers(), "vais1000 is a dataset name, not 1000 speakers");
        assertEquals(1013, catalog.speakerCount("en"));
        assertEquals(67, catalog.speakerCount("vi"));
        var entries = catalog.entries(new VoiceDiscovery(Path.of("/unused"), List.of(), List.of()));
        assertEquals(1079, entries.stream().filter(e -> e.state() == VoiceCatalog.State.DOWNLOADABLE).count());
        assertEquals(1, entries.stream().filter(e -> e.state() == VoiceCatalog.State.UNAVAILABLE).count());
        var vctk = catalog.models().stream().filter(m -> m.id().equals("en_GB-vctk-medium")).findFirst().orElseThrow();
        assertEquals("p260", vctk.identity(96));
        var libritts = catalog.models().stream().filter(m -> m.id().equals("en_US-libritts-high")).findFirst().orElseThrow();
        assertEquals("p3922", libritts.identity(0));
        assertEquals("p2085", libritts.identity(903));
        assertTrue(catalog.models().stream().filter(m -> m.id().contains("25hours")).noneMatch(VoiceCatalog.Model::downloadable));
    }

    private static VoiceCatalog.Model model(java.util.Map<String, Integer> speakers) {
        return new VoiceCatalog.Model("en_US-test-medium", "TEST ONLY", "en_US", 2, speakers, 22050,
            URI.create("https://huggingface.co/rhasspy/piper-voices"), "TEST ONLY", "", false, List.of());
    }
}
