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
    static final int MAX_VOICES = 200;
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
                .filter(path -> !folder.relativize(path).getName(0).toString().startsWith(VoiceInstaller.STAGING_PREFIX))
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
                VoicePack pack = load(folder, model);
                if (!ids.add(pack.id().toLowerCase(Locale.ROOT))) {
                    throw new InvalidVoicePackException("a voice with the id '" + pack.id() + "' already exists");
                }
                voices.add(pack);
            } catch (InvalidVoicePackException ex) {
                problems.add("Skipped voice " + folder.relativize(model) + ": " + ex.getMessage());
            }
        }
        voices.sort(Comparator.comparing((VoicePack v) -> v.name().toLowerCase(Locale.ROOT)).thenComparing(VoicePack::id));
        return new VoiceDiscovery(folder, voices, problems);
    }

    /** Validates one model file and its companions. */
    public VoicePack load(Path folder, Path model) throws InvalidVoicePackException {
        String fileName = model.getFileName().toString();
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
        return new VoicePack(id, name, language, engine, model.toAbsolutePath(), config.toAbsolutePath(), sampleRate,
            clean(text(metadata, "description"), 200));
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

    static JsonObject readJson(Path file) throws InvalidVoicePackException {
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
