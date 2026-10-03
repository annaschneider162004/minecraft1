package com.annaschneider.minecraft1.video.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Discovers voice packs in a folder: no code changes are needed to add a voice. A pack is a model file
 * {@code <id>.onnx} with its config {@code <id>.onnx.json} (the files Piper voices are distributed as), either directly
 * in the folder or in one sub-folder. An optional {@code <id>.voice.json} adds metadata:
 * {@code {"name": "...", "language": "vi_VN", "engine": "piper", "description": "..."}}.
 * Files are size-checked, must stay inside the folder (no links pointing elsewhere) and are rejected with a message
 * when anything is missing, malformed or needs an engine that is not installed in this app.
 */
public final class VoicePackRegistry {
    public static final String MODEL_EXTENSION = ".onnx";
    public static final String CONFIG_SUFFIX = ".onnx.json";
    public static final String METADATA_SUFFIX = ".voice.json";
    public static final String DEFAULT_ENGINE = "piper";
    static final long MAX_JSON_BYTES = 1024 * 1024;
    static final long MAX_MODEL_BYTES = 2L * 1024 * 1024 * 1024;
    static final int MAX_VOICES = 10000;
    private static final String ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._+-]{0,99}";
    private static final String LANGUAGE_PATTERN = "[A-Za-z]{2,3}(?:[_-][A-Za-z0-9]{2,8})*";

    private final Set<String> supportedEngines;

    public VoicePackRegistry(Set<String> supportedEngines) {
        this.supportedEngines = Set.copyOf(supportedEngines);
    }

    public VoiceDiscovery discover(Path folder) {
        List<VoicePack> voices = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (folder == null || !Files.isDirectory(folder)) {
            problems.add("Voices folder not found: " + folder + " (create it and copy voice files into it)");
            return new VoiceDiscovery(folder, voices, problems);
        }
        List<Path> models;
        try (Stream<Path> files = Files.walk(folder, 2)) {
            models = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(MODEL_EXTENSION))
                .sorted().toList();
        } catch (IOException | java.io.UncheckedIOException ex) {
            problems.add("Cannot read the voices folder " + folder + ": " + ex.getMessage());
            return new VoiceDiscovery(folder, voices, problems);
        }
        Set<String> ids = new HashSet<>();
        for (Path model : models) {
            if (voices.size() >= MAX_VOICES) {
                problems.add("Only the first " + MAX_VOICES + " voices are loaded.");
                break;
            }
            try {
                List<VoicePack> speakers = loadSpeakers(folder, model);
                for (VoicePack pack : speakers) {
                    if (ids.contains(pack.id().toLowerCase(Locale.ROOT))) {
                        throw new InvalidVoicePackException("a voice with the id '" + pack.id() + "' already exists");
                    }
                }
                if (voices.size() + speakers.size() > MAX_VOICES) {
                    throw new InvalidVoicePackException("voice capacity of " + MAX_VOICES + " would be exceeded");
                }
                speakers.forEach(pack -> ids.add(pack.id().toLowerCase(Locale.ROOT)));
                voices.addAll(speakers);
            } catch (InvalidVoicePackException ex) {
                problems.add("Skipped voice " + folder.relativize(model) + ": " + ex.getMessage());
            }
        }
        voices.sort(Comparator.comparing((VoicePack v) -> v.name().toLowerCase(Locale.ROOT)).thenComparing(VoicePack::id));
        return new VoiceDiscovery(folder, voices, problems);
    }

    /**
     * Validates one model file and its companions. Returns speaker zero by default, or the speaker selected by
     * optional metadata {@code speaker_id}/{@code speaker_identity}, with the same stable ID as discovery.
     */
    public VoicePack load(Path folder, Path model) throws InvalidVoicePackException {
        return loadValidated(folder, model).selected();
    }

    /**
     * Expands the actual speaker range while sharing the model and config files. Metadata speaker selection is
     * validated but selects only {@link #load(Path, Path)}; discovery always binds the original model ID to speaker zero.
     */
    public List<VoicePack> loadSpeakers(Path folder, Path model) throws InvalidVoicePackException {
        Loaded loaded = loadValidated(folder, model);
        VoicePack base = loaded.selected();
        List<VoicePack> speakers = new ArrayList<>();
        for (Map.Entry<Integer, String> speaker : loaded.speakers().entrySet()) {
            int speakerId = speaker.getKey();
            String identity = speaker.getValue();
            boolean multi = loaded.speakers().size() > 1;
            speakers.add(new VoicePack(speakerId == 0 ? loaded.modelId() : loaded.modelId() + "-speaker-" + speakerId,
                multi ? loaded.name() + " — " + (identity == null ? "Speaker " + speakerId : identity) : loaded.name(),
                base.language(), base.engine(), base.model(), base.config(), base.sampleRate(), base.description(),
                multi || base.speakerId() != null ? speakerId : null, identity));
        }
        return List.copyOf(speakers);
    }

    private record Loaded(VoicePack selected, String modelId, String name, Map<Integer, String> speakers) { }

    private Loaded loadValidated(Path folder, Path model) throws InvalidVoicePackException {
        String fileName = model.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(MODEL_EXTENSION)) {
            throw new InvalidVoicePackException("the model file must end with " + MODEL_EXTENSION);
        }
        String id = fileName.substring(0, fileName.length() - MODEL_EXTENSION.length());
        if (!id.matches(ID_PATTERN)) {
            throw new InvalidVoicePackException("the file name may only use letters, digits, '.', '_', '+' and '-'");
        }
        requireInside(folder, model, "model");
        long modelSize = size(model);
        if (modelSize == 0) {
            throw new InvalidVoicePackException("the model file is empty");
        }
        if (modelSize > MAX_MODEL_BYTES) {
            throw new InvalidVoicePackException("the model file is larger than 2 GB");
        }
        Path config = model.resolveSibling(id + CONFIG_SUFFIX);
        if (!Files.isRegularFile(config)) {
            Path alternative = model.resolveSibling(id + ".json");
            if (!Files.isRegularFile(alternative)) {
                throw new InvalidVoicePackException("missing config file " + id + CONFIG_SUFFIX
                    + " (download it together with the .onnx file)");
            }
            config = alternative;
        }
        requireInside(folder, config, "config");
        JsonObject settings = readJson(config);
        Map<Integer, String> speakers = validateSpeakerConfig(settings);

        JsonObject metadata = new JsonObject();
        Path metadataFile = model.resolveSibling(id + METADATA_SUFFIX);
        if (Files.exists(metadataFile)) {
            requireInside(folder, metadataFile, "metadata");
            metadata = readJson(metadataFile);
        }

        String engine = text(metadata, "engine");
        engine = engine.isBlank() ? DEFAULT_ENGINE : engine.toLowerCase(Locale.ROOT);
        if (!supportedEngines.contains(engine)) {
            throw new InvalidVoicePackException("unsupported engine '" + engine + "' (supported: "
                + String.join(", ", supportedEngines.stream().sorted().toList()) + ")");
        }
        String language = text(metadata, "language");
        if (language.isBlank() && settings.has("language") && settings.get("language").isJsonObject()) {
            language = text(settings.getAsJsonObject("language"), "code");
        }
        if (language.isBlank() && settings.has("espeak") && settings.get("espeak").isJsonObject()) {
            language = text(settings.getAsJsonObject("espeak"), "voice");
        }
        if (language.isBlank()) {
            language = id.contains("-") ? id.substring(0, id.indexOf('-')) : "";
        }
        if (!language.matches(LANGUAGE_PATTERN)) {
            throw new InvalidVoicePackException("unknown language '" + language + "' (set \"language\" in " + id + METADATA_SUFFIX + ")");
        }
        int sampleRate = 0;
        if (settings.has("audio") && settings.get("audio").isJsonObject()) {
            JsonObject audio = settings.getAsJsonObject("audio");
            if (audio.has("sample_rate") && audio.get("sample_rate").isJsonPrimitive()
                && audio.get("sample_rate").getAsJsonPrimitive().isNumber()) {
                sampleRate = audio.get("sample_rate").getAsInt();
            }
        }
        if (sampleRate < 8_000 || sampleRate > 96_000) {
            throw new InvalidVoicePackException("the config has no valid audio.sample_rate - is it a Piper voice config?");
        }
        String name = clean(text(metadata, "name"), 60);
        if (name.isBlank()) {
            name = defaultName(id, settings);
        }
        Integer speakerId = metadata.has("speaker_id") ? strictInteger(metadata.get("speaker_id"), "speaker_id") : null;
        String identity = null;
        if (metadata.has("speaker_identity")) {
            JsonElement value = metadata.get("speaker_identity");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
                throw new InvalidVoicePackException("speaker_identity must be a non-empty string");
            }
            identity = value.getAsString();
            Integer mapped = null;
            for (Map.Entry<Integer, String> entry : speakers.entrySet()) {
                if (identity.equals(entry.getValue())) {
                    mapped = entry.getKey();
                }
            }
            if (mapped == null || speakerId != null && !speakerId.equals(mapped)) {
                throw new InvalidVoicePackException("speaker_identity does not match a configured speaker_id");
            }
            speakerId = mapped;
        }
        if (speakerId != null && !speakers.containsKey(speakerId)) {
            throw new InvalidVoicePackException("speaker_id is outside the configured speaker range");
        }
        if (speakerId == null && speakers.size() > 1) {
            speakerId = 0;
        }
        if (identity == null) {
            identity = speakers.get(speakerId == null ? 0 : speakerId);
        }
        String selectedId = speakerId != null && speakerId != 0 ? id + "-speaker-" + speakerId : id;
        String selectedName = speakers.size() > 1 ? name + " — " + (identity == null ? "Speaker " + speakerId : identity) : name;
        VoicePack selected = new VoicePack(selectedId, selectedName, language, engine, model.toAbsolutePath(), config.toAbsolutePath(),
            sampleRate, clean(text(metadata, "description"), 200), speakerId, identity);
        return new Loaded(selected, id, name, speakers);
    }

    /** Validates Piper speaker metadata; absent counts retain legacy single-speaker behavior. */
    public static Map<Integer, String> validateSpeakerConfig(Path config) throws InvalidVoicePackException {
        if (config == null || !Files.isRegularFile(config)) {
            throw new InvalidVoicePackException("missing model config file: " + config);
        }
        return validateSpeakerConfig(readJson(config));
    }

    public static Map<Integer, String> validateSpeakerConfig(JsonObject settings) throws InvalidVoicePackException {
        int count = settings.has("num_speakers") ? strictInteger(settings.get("num_speakers"), "num_speakers") : 1;
        if (count < 1 || count > MAX_VOICES) {
            throw new InvalidVoicePackException("num_speakers must be between 1 and " + MAX_VOICES);
        }
        Map<Integer, String> speakers = new LinkedHashMap<>();
        for (int id = 0; id < count; id++) {
            speakers.put(id, null);
        }
        if (settings.has("speaker_id_map")) {
            JsonElement map = settings.get("speaker_id_map");
            if (!map.isJsonObject()) {
                throw new InvalidVoicePackException("speaker_id_map must be an object");
            }
            Set<Integer> ids = new HashSet<>();
            for (Map.Entry<String, JsonElement> entry : map.getAsJsonObject().entrySet()) {
                int id = strictInteger(entry.getValue(), "speaker_id_map ID");
                if (entry.getKey().isBlank() || entry.getKey().matches(".*[\\p{Cntrl}\\p{Cf}].*")
                    || id < 0 || id >= count || !ids.add(id)) {
                    throw new InvalidVoicePackException("speaker_id_map must have non-empty identities and unique IDs in range 0.."
                        + (count - 1));
                }
                speakers.put(id, entry.getKey());
            }
        }
        return Collections.unmodifiableMap(speakers);
    }

    private static int strictInteger(JsonElement value, String field) throws InvalidVoicePackException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) {
            throw new InvalidVoicePackException(field + " must be an integer");
        }
        try {
            return Integer.parseInt(value.getAsString());
        } catch (NumberFormatException ex) {
            throw new InvalidVoicePackException(field + " must be an integer within range");
        }
    }

    private static String defaultName(String id, JsonObject settings) {
        String dataset = clean(text(settings, "dataset"), 40);
        String[] parts = id.split("-");
        String speaker = !dataset.isBlank() ? dataset : parts.length > 1 ? parts[1] : id;
        String quality = parts.length > 2 ? " " + parts[parts.length - 1] : "";
        return clean(speaker.substring(0, 1).toUpperCase(Locale.ROOT) + speaker.substring(1) + quality, 60);
    }

    private static void requireInside(Path folder, Path file, String what) throws InvalidVoicePackException {
        try {
            Path root = folder.toRealPath();
            Path real = file.toRealPath();
            if (!real.startsWith(root)) {
                throw new InvalidVoicePackException("the " + what + " file links outside the voices folder");
            }
            if (!Files.isRegularFile(real)) {
                throw new InvalidVoicePackException("the " + what + " file is not a regular file");
            }
        } catch (IOException ex) {
            throw new InvalidVoicePackException("cannot read the " + what + " file: " + ex.getMessage());
        }
    }

    private static long size(Path file) throws InvalidVoicePackException {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            throw new InvalidVoicePackException("cannot read " + file.getFileName() + ": " + ex.getMessage());
        }
    }

    private static JsonObject readJson(Path file) throws InvalidVoicePackException {
        if (size(file) > MAX_JSON_BYTES) {
            throw new InvalidVoicePackException(file.getFileName() + " is too large for a voice config (max 1 MB)");
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                throw new InvalidVoicePackException(file.getFileName() + " is not a JSON object");
            }
            return json.getAsJsonObject();
        } catch (IOException | JsonParseException ex) {
            throw new InvalidVoicePackException(file.getFileName() + " is not valid JSON: " + ex.getMessage());
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString().strip() : "";
    }

    private static String clean(String text, int max) {
        String value = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        return value.length() > max ? value.substring(0, max).strip() : value;
    }
}
