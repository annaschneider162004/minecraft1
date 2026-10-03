package com.annaschneider.minecraft1.video.voice;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Offline, curated model metadata. Catalog entries are not installed voices. */
public final class VoiceCatalog {
    public enum State { INSTALLED, DOWNLOADABLE, UNAVAILABLE }

    public record Asset(String path, URI url, long sizeBytes, String hashAlgorithm, String hash) { }

    public record Model(String id, String name, String language, int numSpeakers, Map<String, Integer> speakerIdMap,
                        int sampleRate, URI source, String license, String restrictions, boolean downloadable,
                        List<Asset> files) {
        public Model {
            speakerIdMap = Map.copyOf(speakerIdMap);
            files = List.copyOf(files);
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,99}")
                || !Set.of("en_US", "en_GB", "vi_VN").contains(language)
                || numSpeakers < 1 || numSpeakers > 10_000 || sampleRate < 8_000 || sampleRate > 96_000) {
                throw new IllegalArgumentException("Invalid catalog model metadata");
            }
            if (numSpeakers > 1 && speakerIdMap.size() != numSpeakers) {
                throw new IllegalArgumentException("Catalog must identify every multi-model speaker");
            }
            Set<Integer> ids = new HashSet<>();
            for (var speaker : speakerIdMap.entrySet()) {
                if (speaker.getKey().isBlank() || speaker.getValue() == null || speaker.getValue() < 0
                    || speaker.getValue() >= numSpeakers || !ids.add(speaker.getValue())) {
                    throw new IllegalArgumentException("Invalid catalog speaker map");
                }
            }
        }

        public long downloadBytes() {
            return files.stream().mapToLong(Asset::sizeBytes).sum();
        }

        public String voiceId(int speaker) {
            return speaker == 0 ? id : id + "-speaker-" + speaker;
        }

        public String identity(int speaker) {
            return speakerIdMap.entrySet().stream().filter(e -> e.getValue() == speaker)
                .map(Map.Entry::getKey).findFirst().orElse("");
        }
    }

    public record Entry(String id, String name, String language, Model model, VoicePack installedVoice,
                        State state, String source) {
        public boolean installed() { return installedVoice != null; }
    }

    private final List<Model> models;
    private final Map<String, Map<Integer, String>> speakerIdentities;

    public VoiceCatalog(List<Model> models) {
        this.models = List.copyOf(models);
        Set<String> ids = new HashSet<>();
        Map<String, Map<Integer, String>> identities = new java.util.HashMap<>();
        for (Model model : models) {
            if (!ids.add(model.id())) {
                throw new IllegalArgumentException("Duplicate catalog model " + model.id());
            }
            Map<Integer, String> reverse = new java.util.HashMap<>();
            model.speakerIdMap().forEach((identity, id) -> reverse.put(id, identity));
            identities.put(model.id(), Map.copyOf(reverse));
        }
        speakerIdentities = Map.copyOf(identities);
    }

    public static VoiceCatalog bundled() throws IOException {
        var stream = VoiceCatalog.class.getResourceAsStream("/voices/catalog.json");
        if (stream == null) {
            throw new IOException("Bundled voice catalog is missing; installed voices remain usable.");
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            // URI fields are strings in the portable, offline resource.
            var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            List<Model> models = new ArrayList<>();
            Gson gson = new Gson();
            for (var element : root.getAsJsonArray("models")) {
                var object = element.getAsJsonObject();
                Map<String, Integer> speakers = new java.util.LinkedHashMap<>();
                if (object.has("speakers")) {
                    int id = 0;
                    for (var speaker : object.getAsJsonArray("speakers")) {
                        if (speakers.put(speaker.getAsString(), id++) != null) {
                            throw new IllegalArgumentException("Duplicate catalog speaker identity");
                        }
                    }
                } else {
                    object.getAsJsonObject("speakerIdMap").entrySet()
                        .forEach(e -> speakers.put(e.getKey(), e.getValue().getAsInt()));
                }
                List<Asset> assets = new ArrayList<>();
                for (var asset : object.getAsJsonArray("files")) {
                    assets.add(gson.fromJson(asset, Asset.class));
                }
                models.add(new Model(object.get("id").getAsString(), object.get("name").getAsString(),
                    object.get("language").getAsString(), object.get("numSpeakers").getAsInt(), speakers,
                    object.get("sampleRate").getAsInt(), URI.create(object.get("source").getAsString()),
                    object.get("license").getAsString(), object.get("restrictions").getAsString(),
                    object.get("downloadable").getAsBoolean(), assets));
            }
            return new VoiceCatalog(models);
        } catch (RuntimeException ex) {
            throw new IOException("Bundled voice catalog is invalid; installed voices remain usable.", ex);
        }
    }

    public List<Model> models() { return models; }

    /** Counts genuine identities in curated, non-overlapping model datasets, not quality variants or presets. */
    public int speakerCount(String language) {
        return models.stream().filter(m -> language == null || language.isBlank()
            || m.language().startsWith(language)).mapToInt(Model::numSpeakers).sum();
    }

    public List<Entry> entries(VoiceDiscovery installed) {
        Map<String, VoicePack> local = new java.util.LinkedHashMap<>();
        installed.voices().forEach(v -> local.putIfAbsent(v.id(), v));
        List<Entry> entries = new ArrayList<>();
        for (Model model : models) {
            Map<Integer, String> identities = speakerIdentities.get(model.id());
            for (int speaker = 0; speaker < model.numSpeakers(); speaker++) {
                String id = model.voiceId(speaker);
                String identity = identities.getOrDefault(speaker, "");
                VoicePack voice = local.get(id);
                // Matching names alone do not establish that a local pack is this catalog speaker.
                if (voice != null && (!PiperTtsEngine.ID.equals(voice.engine())
                    || !model.language().equals(voice.language())
                    || (model.numSpeakers() > 1 && (!Integer.valueOf(speaker).equals(voice.speakerId())
                        || !identity.equals(voice.speakerIdentity()))))) {
                    // Keep the conflicting local pack, but never offer a second entry with the same selection ID.
                    continue;
                }
                if (voice != null) {
                    local.remove(id);
                }
                String name = model.name() + (model.numSpeakers() > 1 ? " / " + identity : "");
                entries.add(new Entry(id, name, model.language(), model, voice,
                    voice != null ? State.INSTALLED : model.downloadable() ? State.DOWNLOADABLE : State.UNAVAILABLE,
                    "Piper catalog"));
            }
        }
        local.values().forEach(v -> entries.add(new Entry(v.id(), v.name(), v.language(), null, v, State.INSTALLED,
            XttsTtsEngine.ID.equals(v.engine()) ? "Cloned profile" : "Local Piper")));
        return List.copyOf(entries);
    }

    public static List<Entry> filter(List<Entry> entries, String language, String query, boolean favoritesOnly,
                                     Set<String> favorites) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return entries.stream()
            .filter(e -> language == null || language.isBlank() || e.language().startsWith(language))
            .filter(e -> !favoritesOnly || favorites.contains(e.id()))
            .filter(e -> needle.isEmpty() || (e.id() + " " + e.name() + " " + e.language() + " " + e.source()
                + " " + e.state()).toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }
}
