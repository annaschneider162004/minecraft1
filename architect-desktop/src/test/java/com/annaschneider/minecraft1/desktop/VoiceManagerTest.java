package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.video.voice.VoiceCatalog;
import com.annaschneider.minecraft1.video.voice.VoiceDiscovery;
import com.annaschneider.minecraft1.video.voice.VoicePack;
import com.annaschneider.minecraft1.video.BuildContext;
import com.annaschneider.minecraft1.video.ExecutableLocator;
import com.annaschneider.minecraft1.video.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class VoiceManagerTest {
    @Test
    void displayedManagerLaunchesFiltersAndChoosesExactOfflineSpeaker(@TempDir Path folder) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Files.write(folder.resolve("en_US-test-medium.onnx"), new byte[] {1});
        Files.writeString(folder.resolve("en_US-test-medium.onnx.json"),
            "{\"audio\":{\"sample_rate\":22050},\"language\":{\"code\":\"en_US\"},"
                + "\"num_speakers\":2,\"speaker_id_map\":{\"speakerB\":1,\"speakerA\":0}}");
        VoiceCatalog bundled = VoiceCatalog.bundled();
        int catalogRows = bundled.speakerCount("en") + bundled.speakerCount("vi");
        Preferences node = Preferences.userRoot().node("architect-voice-manager-ui-test-" + UUID.randomUUID());
        AtomicReference<VideoStudioWindow> window = new AtomicReference<>();
        AtomicReference<VoiceManagerWindow> manager = new AtomicReference<>();
        try {
            VideoStudioSettings settings = new VideoStudioSettings(node);
            settings.setVoicesFolder(folder.toString());
            settings.setVoiceId("missing-saved-speaker");
            ProcessRunner never = (command, stdin, timeout) -> { throw new AssertionError("no backend should run"); };
            VideoStudio studio = new VideoStudio(settings, new ExecutableLocator("", false, name -> null), never);
            SwingUtilities.invokeAndWait(() -> {
                window.set(new VideoStudioWindow(studio, BuildContext::empty, () -> "No build", folder));
                window.get().showStudio();
            });
            awaitWorker(window.get());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("missing-saved-speaker", settings.voiceId());
                assertEquals(VoiceSelection.NONE, ((JComboBox<?>) field(window.get(), "voiceBox")).getSelectedItem());
                button(window.get(), "Manage voices / Quản lý giọng...").doClick();
                manager.set((VoiceManagerWindow) field(window.get(), "voiceManager"));
            });
            awaitWorker(manager.get());
            String exactId = "en_US-test-medium-speaker-1";
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(manager.get().isShowing());
                assertTrue(((javax.swing.JTextArea) field(manager.get(), "details")).getText()
                    .contains(VoiceManagerWindow.PIPER_REQUIREMENT), "Piper prerequisite is shown before selecting a row");
                manager.get().setSize(680, 480);
                manager.get().validate();
                JTable table = (JTable) field(manager.get(), "table");
                assertTrue(catalogRows >= 1000, "large bundled catalog remains browsable");
                assertEquals(catalogRows + 2, table.getRowCount(), "all catalog IDs plus two local speakers are shown");
                ((JComboBox<?>) field(manager.get(), "state")).setSelectedItem("INSTALLED");
                applyFilters(manager.get());
                assertEquals(2, table.getRowCount(), "installed filter excludes every downloadable catalog entry");
                for (int row = 0; row < table.getRowCount(); row++) {
                    if (exactId.equals(table.getValueAt(row, 5))) {
                        table.setRowSelectionInterval(row, row);
                        break;
                    }
                }
                assertEquals(exactId, table.getValueAt(table.getSelectedRow(), 5));
                assertTrue(((javax.swing.JTextArea) field(manager.get(), "details")).getText()
                    .contains(VoiceManagerWindow.PIPER_REQUIREMENT), "Piper executable prerequisite is explained upfront");
                assertTrue(button(manager.get(), "Preview installed").isEnabled());
                assertFalse(button(manager.get(), "Install / Retry").isEnabled());
                button(manager.get(), "Use this voice").doClick();
                assertEquals(exactId, settings.voiceId());
                assertEquals(exactId, ((VoicePack) ((JComboBox<?>) field(window.get(), "voiceBox")).getSelectedItem()).id());
                button(manager.get(), "Favorite").doClick();
            });
            awaitWorker(manager.get());
            assertTrue(settings.favoriteVoiceIds().contains(exactId));
            SwingUtilities.invokeAndWait(() -> {
                JCheckBox favorites = (JCheckBox) field(manager.get(), "favoritesOnly");
                favorites.setSelected(true);
                applyFilters(manager.get());
                JTable table = (JTable) field(manager.get(), "table");
                assertEquals(1, table.getRowCount(), "favorite and installed filters combine");
                assertEquals(exactId, table.getValueAt(0, 5));
                favorites.setSelected(false);
                JComboBox<?> language = (JComboBox<?>) field(manager.get(), "language");
                language.setSelectedItem("vi");
                JComboBox<?> state = (JComboBox<?>) field(manager.get(), "state");
                state.setSelectedItem("DOWNLOADABLE");
                applyFilters(manager.get());
                assertTrue(table.getRowCount() > 0);
                for (int row = 0; row < table.getRowCount(); row++) {
                    assertTrue(table.getValueAt(row, 2).toString().startsWith("vi"));
                    assertEquals("DOWNLOADABLE", table.getValueAt(row, 4));
                }
                table.setRowSelectionInterval(0, 0);
                assertTrue(((javax.swing.JTextArea) field(manager.get(), "details")).getText()
                    .contains(VoiceManagerWindow.PIPER_REQUIREMENT), "downloadable model also needs separate Piper");
                assertFalse(button(manager.get(), "Preview installed").isEnabled());
                assertFalse(button(manager.get(), "Use this voice").isEnabled());
                assertTrue(button(manager.get(), "Install / Retry").isEnabled());
                state.setSelectedItem("UNAVAILABLE");
                applyFilters(manager.get());
                assertTrue(table.getRowCount() > 0);
                table.setRowSelectionInterval(0, 0);
                assertFalse(button(manager.get(), "Preview installed").isEnabled());
                assertFalse(button(manager.get(), "Use this voice").isEnabled());
                assertFalse(button(manager.get(), "Install / Retry").isEnabled());
                assertEquals(exactId, settings.voiceId(), "filtering must not change the chosen main speaker");
                button(manager.get(), "Refresh offline").doClick();
            });
            awaitWorker(manager.get());
            assertEquals(exactId, settings.voiceId(), "offline refresh preserves exact selected speaker");
        } finally {
            if (window.get() != null) {
                SwingUtilities.invokeAndWait(window.get()::dispose);
            }
            node.removeNode();
        }
    }

    private static void awaitWorker(Object window) throws Exception {
        ((ExecutorService) field(window, "worker")).submit(() -> { }).get(20, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void applyFilters(VoiceManagerWindow manager) {
        ((javax.swing.Timer) field(manager, "filterTimer")).stop();
        try {
            var filter = VoiceManagerWindow.class.getDeclaredMethod("filter");
            filter.setAccessible(true);
            filter.invoke(manager);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static JButton button(Container root, String text) {
        JButton result = findButton(root, text);
        if (result == null) {
            throw new AssertionError("Missing button: " + text);
        }
        return result;
    }

    private static JButton findButton(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void favoritesPersistIndividuallyBeyondAThousandRows() throws Exception {
        Preferences node = Preferences.userRoot().node("architect-voice-manager-test-" + UUID.randomUUID());
        try {
            VideoStudioSettings settings = new VideoStudioSettings(node);
            settings.setVoiceId("shared-model-speaker-7");
            for (int i = 0; i < 1100; i++) {
                settings.setVoiceFavorite("speaker-" + i, true);
            }
            String unusualId = "model/with/slashes/" + "long-id-".repeat(20) + "tiếng-Việt";
            settings.setVoiceFavorite(unusualId, true);
            node.flush();
            VideoStudioSettings reopened = new VideoStudioSettings(Preferences.userRoot().node(node.absolutePath()));
            assertEquals(1101, reopened.favoriteVoiceIds().size());
            assertTrue(reopened.favoriteVoiceIds().contains(unusualId));
            assertEquals(1101, node.node("voice-favorites").childrenNames().length);
            assertEquals(0, node.node("voice-favorites").keys().length, "no giant serialized favorites preference");
            reopened.setVoiceFavorite("speaker-7", false);
            reopened.setVoiceFavorite("not-present", false);
            reopened.setVoiceFavorite(unusualId, false);
            assertEquals(1099, new VideoStudioSettings(node).favoriteVoiceIds().size());
            assertFalse(reopened.favoriteVoiceIds().contains("speaker-7"));
            assertEquals("shared-model-speaker-7", reopened.voiceId(), "favorites must not change exact selection");
            assertThrows(IllegalArgumentException.class, () -> settings.setVoiceFavorite(" ", true));
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    @Test
    void missingSavedVoiceNeverSelectsFirstSpeakerAndExactSpeakerSurvivesRebuilds() {
        VoicePack first = pack("shared-model", "First", "en_US", "piper");
        VoicePack seventh = pack("shared-model-speaker-7", "Seventh", "en_US", "piper");
        VoicePack clone = pack("clone-profile", "Clone", "en", "xtts");
        List<VoicePack> installed = List.of(first, seventh, clone);
        assertEquals(VoiceSelection.NONE, VoiceSelection.model(installed, "missing-speaker", false).getSelectedItem());
        assertEquals(VoiceSelection.NONE, VoiceSelection.model(installed, "", false).getSelectedItem());
        assertSame(seventh, VoiceSelection.model(installed, seventh.id(), false).getSelectedItem());
        assertEquals(VoiceSelection.NONE, VoiceSelection.model(installed, seventh.id(), true).getSelectedItem());
        assertSame(clone, VoiceSelection.model(installed, clone.id(), true).getSelectedItem());
        assertEquals(VoiceSelection.NONE, VoiceSelection.model(installed, clone.id(), false).getSelectedItem());
        assertSame(seventh, VoiceSelection.model(List.of(seventh, first), seventh.id(), false).getSelectedItem());
    }

    @Test
    void verifiedInventorySeparatesUnavailableSpeakersFromDownloadableRows() {
        VoiceCatalog.Model english = model("english-model", "en_US");
        VoiceCatalog.Model restricted = new VoiceCatalog.Model("restricted-model", "Restricted", "vi_VN", 1,
            Map.of(), 22050, URI.create("https://example.org/card"), "Restricted license", "No downloads approved",
            false, List.of());
        VoiceCatalog catalog = new VoiceCatalog(List.of(english, restricted));
        VoiceDiscovery installed = new VoiceDiscovery(Path.of("voices"),
            List.of(pack("local", "Local", "en_US", "piper"), pack("clone", "Clone", "en", "xtts")), List.of());
        String summary = VoiceManagerWindow.inventoryText(catalog, installed, catalog.entries(installed));
        assertTrue(summary.contains("verified model-local catalog IDs (includes unavailable): EN 1 / VI 1"), summary);
        assertTrue(summary.contains("Downloadable now: EN 1 / VI 0; unavailable: 1"), summary);
        assertTrue(summary.contains("model-local catalog gap to1000: 998"), summary);
        assertTrue(summary.contains("Installed Piper models: 1; model-local IDs: 1; installed Piper gap to1000: 999"), summary);
        assertTrue(summary.contains("Cloned profiles: 1"), summary);
        assertTrue(summary.contains("not count as model inventory or target progress"), summary);
        assertTrue(summary.contains("Not independently deduplicated people"), summary);
        assertTrue(summary.contains("review licenses"), summary);
    }

    @Test
    void inMemoryFiltersCombineLanguageSearchSourceStateAndFavoritesAcrossLargeCatalog() {
        List<VoiceCatalog.Entry> entries = new ArrayList<>();
        VoiceCatalog.Model english = model("english-model", "en_US");
        VoiceCatalog.Model vietnamese = model("vietnamese-model", "vi_VN");
        for (int i = 0; i < 1500; i++) {
            boolean vi = i % 2 == 0;
            entries.add(new VoiceCatalog.Entry("voice-" + i, (vi ? "Vietnamese " : "English ") + i,
                vi ? "vi_VN" : "en_US", vi ? vietnamese : english, null,
                i % 3 == 0 ? VoiceCatalog.State.UNAVAILABLE : VoiceCatalog.State.DOWNLOADABLE, "Piper catalog"));
        }
        VoicePack local = pack("local-speaker-7", "Offline speaker", "vi_VN", "piper");
        VoicePack clone = pack("clone-profile", "English clone", "en", "xtts");
        entries.add(new VoiceCatalog.Entry(local.id(), local.name(), local.language(), null, local,
            VoiceCatalog.State.INSTALLED, "Local Piper"));
        entries.add(new VoiceCatalog.Entry(clone.id(), clone.name(), clone.language(), null, clone,
            VoiceCatalog.State.INSTALLED, "Cloned profile"));
        Set<String> favorites = Set.of("voice-10", "voice-11", local.id(), clone.id());
        assertEquals(1502, filter(entries, "all", "", false, favorites, "All sources", "All states").size());
        assertEquals(751, filter(entries, "vi", "", false, favorites, "All sources", "All states").size());
        assertEquals(List.of("voice-10"), ids(filter(entries, "vi", " VIETNAMESE ", true, favorites,
            "Piper catalog", "DOWNLOADABLE")));
        assertEquals(List.of(local.id()), ids(filter(entries, "vi", "offline", true, favorites, "Local Piper", "INSTALLED")));
        assertEquals(List.of(clone.id()), ids(filter(entries, "en", "", true, favorites, "Cloned profile", "INSTALLED")));
        assertEquals(500, filter(entries, "all", "", false, favorites, "Piper catalog", "UNAVAILABLE").size());
        assertTrue(filter(entries, "vi", "", false, favorites, "Cloned profile", "All states").isEmpty());
        assertEquals(1502, entries.size(), "filtering must not mutate offline catalog");
    }

    private static VoiceCatalog.Model model(String id, String language) {
        return new VoiceCatalog.Model(id, id, language, 1, Map.of(), 22050, URI.create("https://example.org/card"),
            "Example license", "Review original card", true, List.of());
    }

    private static List<VoiceCatalog.Entry> filter(List<VoiceCatalog.Entry> entries, String language, String query,
                                                  boolean favoritesOnly, Set<String> favorites, String source, String state) {
        return VoiceManagerWindow.filterEntries(entries, language, query, favoritesOnly, favorites, source, state);
    }

    private static List<String> ids(List<VoiceCatalog.Entry> entries) {
        return entries.stream().map(VoiceCatalog.Entry::id).toList();
    }

    private static VoicePack pack(String id, String name, String language, String engine) {
        return new VoicePack(id, name, language, engine, Path.of("shared.onnx"), Path.of("shared.onnx.json"), 22050, "");
    }
}
