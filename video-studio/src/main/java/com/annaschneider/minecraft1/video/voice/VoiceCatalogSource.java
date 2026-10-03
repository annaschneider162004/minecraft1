package com.annaschneider.minecraft1.video.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the verified voice catalog: metadata only (ids, languages, speakers, file sizes and checksums), never model
 * files. The bundled {@value #RESOURCE} lists the English and Vietnamese Piper models of the official Piper voice list.
 * Entries that fail validation are skipped with a message; a missing or broken catalog leaves only installed voices.
 */
public final class VoiceCatalogSource {
    public static final String RESOURCE = "voice-catalog.json";
    public static final Set<String> ALLOWED_HOSTS = Set.of("huggingface.co");
    static final int MAX_MODELS = 10_000;
    static final long MAX_CATALOG_BYTES = 16L * 1024 * 1024;
    private static final String ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._+-]{0,99}";
    private static final String LANGUAGE_PATTERN = "[A-Za-z]{2,3}(?:[_-][A-Za-z0-9]{2,8})*";
    private static final String PATH_PATTERN = "[A-Za-z0-9_+-]+(?:/[A-Za-z0-9_.+-]+)*";

    /** Parsed catalog: the valid models and one message per rejected entry. */
    public record Parsed(String source, List<DownloadableVoice> models, List<String> problems) {
        public Parsed {
            models = List.copyOf(models);
            problems = List.copyOf(problems);
        }

        public static Parsed empty(String problem) {
            return new Parsed("", List.of(), List.of(problem));
        }
    }

    private VoiceCatalogSource() {
    }

    /** The catalog shipped with the app. */
    public static Parsed bundled() {
        try (InputStream in = VoiceCatalogSource.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Parsed.empty("The verified voice catalog is not available; only installed voices are shown.");
            }
            byte[] bytes = in.readNBytes((int) MAX_CATALOG_BYTES + 1);
            if (bytes.length > MAX_CATALOG_BYTES) {
                return Parsed.empty("The verified voice catalog is too large; only installed voices are shown.");
            }
            return parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return Parsed.empty("Cannot read the verified voice catalog (" + ex.getMessage() + "); only installed voices are shown.");
        }
    }

    public static Parsed parse(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return Parsed.empty("The voice catalog is not a JSON object.");
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException ex) {
            return Parsed.empty("The voice catalog is not valid JSON: " + ex.getMessage());
        }
        URI base;
        try {
            base = baseUrl(text(root, "baseUrl"));
        } catch (IllegalArgumentException ex) {
            return Parsed.empty("The voice catalog has an unsafe download address: " + ex.getMessage());
        }
        JsonElement modelsJson = root.get("models");
        if (modelsJson == null || !modelsJson.isJsonArray()) {
            return Parsed.empty("The voice catalog has no \"models\" list.");
        }
        List<DownloadableVoice> models = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : modelsJson.getAsJsonArray()) {
            if (models.size() >= MAX_MODELS) {
                problems.add("Only the first " + MAX_MODELS + " catalog models are loaded.");
                break;
            }
            String id = element.isJsonObject() ? text(element.getAsJsonObject(), "id") : "";
            try {
                DownloadableVoice model = model(element, base);
                if (!ids.add(model.modelId().toLowerCase(Locale.ROOT))) {
                    throw new InvalidVoicePackException("the id is listed twice");
                }
                models.add(model);
            } catch (InvalidVoicePackException | RuntimeException ex) {
                problems.add("Skipped catalog voice " + (id.isEmpty() ? "?" : id) + ": " + ex.getMessage());
            }
        }
        return new Parsed(text(root, "source"), models, problems);
    }

    static URI baseUrl(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || !ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) || uri.getUserInfo() != null
            || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() != -1 || !value.endsWith("/")) {
            throw new IllegalArgumentException("'" + value + "' (HTTPS folder on " + String.join(", ", ALLOWED_HOSTS) + " required)");
        }
        return uri;
    }

    private static DownloadableVoice model(JsonElement element, URI base) throws InvalidVoicePackException {
        if (!element.isJsonObject()) {
            throw new InvalidVoicePackException("not a JSON object");
        }
        JsonObject json = element.getAsJsonObject();
        String id = text(json, "id");
        if (!id.matches(ID_PATTERN)) {
            throw new InvalidVoicePackException("invalid id");
        }
        String language = text(json, "language");
        if (!language.matches(LANGUAGE_PATTERN)) {
            throw new InvalidVoicePackException("invalid language '" + language + "'");
        }
        String engine = text(json, "engine").toLowerCase(Locale.ROOT);
        if (!PiperTtsEngine.ID.equals(engine)) {
            throw new InvalidVoicePackException("unsupported engine '" + engine + "'");
        }
        List<DownloadableVoice.CatalogFile> files = new ArrayList<>();
        JsonElement filesJson = json.get("files");
        if (filesJson == null || !filesJson.isJsonArray()) {
            throw new InvalidVoicePackException("no files");
        }
        for (JsonElement fileJson : filesJson.getAsJsonArray()) {
            if (!fileJson.isJsonObject()) {
                throw new InvalidVoicePackException("invalid file entry");
            }
            JsonObject file = fileJson.getAsJsonObject();
            String path = text(file, "path");
            if (!path.matches(PATH_PATTERN) || path.contains("..")) {
                throw new InvalidVoicePackException("invalid file path '" + path + "'");
            }
            long size = number(file, "size");
            if (size <= 0 || size > VoicePackRegistry.MAX_MODEL_BYTES) {
                throw new InvalidVoicePackException("invalid size for " + path);
            }
            String md5 = text(file, "md5").toLowerCase(Locale.ROOT);
            if (!md5.matches("[0-9a-f]{32}")) {
                throw new InvalidVoicePackException("invalid checksum for " + path);
            }
            files.add(new DownloadableVoice.CatalogFile(path, size, md5));
        }
        List<String> names = files.stream().map(DownloadableVoice.CatalogFile::fileName).sorted().toList();
        if (!names.equals(List.of(id + VoicePackRegistry.MODEL_EXTENSION, id + VoicePackRegistry.CONFIG_SUFFIX))) {
            throw new InvalidVoicePackException("needs exactly " + id + ".onnx and " + id + ".onnx.json");
        }
        List<VoiceSpeaker> speakers = new ArrayList<>();
        JsonElement speakersJson = json.get("speakers");
        if (speakersJson != null) {
            if (!speakersJson.isJsonArray()) {
                throw new InvalidVoicePackException("speakers must be a list");
            }
            JsonArray array = speakersJson.getAsJsonArray();
            if (array.size() < 2 || array.size() > PiperSpeakers.MAX_SPEAKERS) {
                throw new InvalidVoicePackException("a multi-speaker model needs 2-" + PiperSpeakers.MAX_SPEAKERS + " speakers");
            }
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < array.size(); i++) {
                JsonElement name = array.get(i);
                if (!name.isJsonPrimitive() || name.getAsString().isBlank() || name.getAsString().length() > 60
                    || name.getAsString().matches("(?s).*[\\p{Cntrl}\\p{Cf}].*") || !seen.add(name.getAsString())) {
                    throw new InvalidVoicePackException("invalid or duplicate speaker name at index " + i);
                }
                speakers.add(new VoiceSpeaker(i, name.getAsString()));
            }
        }
        String name = text(json, "name");
        String quality = text(json, "quality");
        if (!name.matches("[A-Za-z0-9 ._+-]{0,60}") || !quality.matches("[A-Za-z0-9_]{0,20}")) {
            throw new InvalidVoicePackException("invalid name or quality");
        }
        return new DownloadableVoice(id, name, language, engine, quality, files, speakers, base);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString().strip() : "";
    }

    private static long number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : -1;
    }
}
