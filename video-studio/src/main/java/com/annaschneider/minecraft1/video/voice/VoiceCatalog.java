package com.annaschneider.minecraft1.video.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The unified voice catalog: installed Piper packs, cloned profiles and verified downloadable models, with every
 * multi-speaker model expanded into one entry per speaker. Only real entries are listed - installed files and verified
 * catalog metadata - so the size of the catalog is exactly what the data provides. Immutable; filter it freely from
 * any thread.
 */
public final class VoiceCatalog {
    private final Map<String, VoiceCatalogEntry> entries;
    private final List<String> problems;
    private final String source;

    private VoiceCatalog(Map<String, VoiceCatalogEntry> entries, List<String> problems, String source) {
        this.entries = Collections.unmodifiableMap(entries);
        this.problems = List.copyOf(problems);
        this.source = source == null ? "" : source;
    }

    /**
     * Merges installed voices (packs and cloned profiles) with the verified catalog. A catalog model that is already
     * installed is listed once, as installed. Installed packs whose speaker list cannot be read are skipped with a
     * message rather than spoken with a guessed speaker.
     */
    public static VoiceCatalog build(VoiceDiscovery installed, VoiceCatalogSource.Parsed catalog) {
        List<String> problems = new ArrayList<>(installed == null ? List.of() : installed.problems());
        Map<String, DownloadableVoice> available = new LinkedHashMap<>();
        if (catalog != null) {
            problems.addAll(catalog.problems());
            catalog.models().forEach(model -> available.put(model.modelId().toLowerCase(Locale.ROOT), model));
        }
        Map<String, VoiceCatalogEntry> entries = new LinkedHashMap<>();
        List<VoicePack> packs = installed == null ? List.of() : installed.voices();
        for (VoicePack pack : packs) {
            DownloadableVoice verified = available.remove(pack.id().toLowerCase(Locale.ROOT));
            if (XttsTtsEngine.ID.equals(pack.engine())) {
                add(entries, problems, new VoiceCatalogEntry(pack.id(), pack.name(), pack.language(), pack.engine(),
                    VoiceCatalogEntry.Source.CLONED, pack.description(), null, 1, pack, null));
                continue;
            }
            List<VoiceSpeaker> speakers;
            try {
                speakers = PiperSpeakers.read(pack.config());
            } catch (InvalidVoicePackException ex) {
                problems.add("Skipped voice " + pack.id() + ": " + ex.getMessage());
                continue;
            }
            if (speakers.isEmpty()) {
                add(entries, problems, new VoiceCatalogEntry(pack.id(), pack.name(), pack.language(), pack.engine(),
                    VoiceCatalogEntry.Source.INSTALLED_PACK, pack.description(), null, 1, pack, verified));
            } else {
                for (VoiceSpeaker speaker : speakers) {
                    add(entries, problems, new VoiceCatalogEntry(pack.id(), pack.name(), pack.language(), pack.engine(),
                        VoiceCatalogEntry.Source.INSTALLED_PACK, pack.description(), speaker, speakers.size(), pack, verified));
                }
            }
        }
        List<DownloadableVoice> remaining = new ArrayList<>(available.values());
        remaining.sort(Comparator.comparing(DownloadableVoice::language).thenComparing(DownloadableVoice::modelId));
        for (DownloadableVoice model : remaining) {
            String description = "Verified " + model.engine() + " model, " + VoiceCatalogEntry.megabytes(model.totalBytes())
                + " download";
            if (model.speakers().isEmpty()) {
                add(entries, problems, new VoiceCatalogEntry(model.modelId(), model.displayName(), model.language(), model.engine(),
                    VoiceCatalogEntry.Source.CATALOG, description, null, 1, null, model));
            } else {
                for (VoiceSpeaker speaker : model.speakers()) {
                    add(entries, problems, new VoiceCatalogEntry(model.modelId(), model.displayName(), model.language(),
                        model.engine(), VoiceCatalogEntry.Source.CATALOG, description, speaker, model.speakers().size(), null, model));
                }
            }
        }
        return new VoiceCatalog(entries, problems, catalog == null ? "" : catalog.source());
    }

    private static void add(Map<String, VoiceCatalogEntry> entries, List<String> problems, VoiceCatalogEntry entry) {
        if (entries.putIfAbsent(entry.id(), entry) != null) {
            problems.add("Skipped duplicate voice id " + entry.id());
        }
    }

    public List<VoiceCatalogEntry> entries() {
        return List.copyOf(entries.values());
    }

    public int size() {
        return entries.size();
    }

    public Optional<VoiceCatalogEntry> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entries.get(id));
    }

    /** The exact voice for {@code id}, or a clear error when it is unknown or not installed. */
    public VoiceSelection select(String id) throws NarrationException {
        VoiceCatalogEntry entry = find(id).orElseThrow(() -> new NarrationException("Voice '" + id
            + "' is not in the voice catalog. Click Refresh and choose another voice."));
        return entry.selection();
    }

    /** Entries that pass {@code filter}, in catalog order. */
    public List<VoiceCatalogEntry> filter(VoiceFilter filter) {
        List<String> words = filter.words();
        List<VoiceCatalogEntry> result = new ArrayList<>();
        for (VoiceCatalogEntry entry : entries.values()) {
            if (filter.matches(entry, words)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Distinct language codes, sorted. */
    public List<String> languages() {
        TreeSet<String> languages = new TreeSet<>();
        entries.values().forEach(entry -> languages.add(entry.language()));
        return List.copyOf(languages);
    }

    /** Distinct engine ids, sorted. */
    public List<String> engines() {
        TreeSet<String> engines = new TreeSet<>();
        entries.values().forEach(entry -> engines.add(entry.engine()));
        return List.copyOf(engines);
    }

    public long installedCount() {
        return entries.values().stream().filter(VoiceCatalogEntry::installed).count();
    }

    public long downloadableCount() {
        return entries.values().stream().filter(VoiceCatalogEntry::downloadable).count();
    }

    /** Number of distinct models (a multi-speaker model counts once). */
    public long modelCount() {
        return entries.values().stream().map(VoiceCatalogEntry::modelId).distinct().count();
    }

    public List<String> problems() {
        return problems;
    }

    /** Where the verified catalog metadata comes from, or empty. */
    public String source() {
        return source;
    }

    /** One line for the log, e.g. {@code Voice catalog: 2075 entries from 43 models (3 installed, 2072 verified downloadable)}. */
    public String summary() {
        return String.format(Locale.ROOT, "Voice catalog: %d entries from %d models (%d installed, %d verified downloadable)",
            size(), modelCount(), installedCount(), downloadableCount());
    }
}
