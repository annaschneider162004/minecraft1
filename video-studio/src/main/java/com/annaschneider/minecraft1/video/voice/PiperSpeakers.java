package com.annaschneider.minecraft1.video.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the speakers of a Piper model from its {@code .onnx.json} config ({@code num_speakers} and
 * {@code speaker_id_map}). Single-speaker models have no speaker list.
 */
public final class PiperSpeakers {
    static final int MAX_SPEAKERS = 10_000;

    private PiperSpeakers() {
    }

    /** Speakers of the model whose config is {@code config}, ordered by index; empty for a single-speaker model. */
    public static List<VoiceSpeaker> read(Path config) throws InvalidVoicePackException {
        if (config == null) {
            return List.of();
        }
        return parse(VoicePackRegistry.readJson(config));
    }

    public static List<VoiceSpeaker> parse(JsonObject settings) throws InvalidVoicePackException {
        int count = 1;
        JsonElement number = settings.get("num_speakers");
        if (number != null && number.isJsonPrimitive() && number.getAsJsonPrimitive().isNumber()) {
            count = number.getAsInt();
        }
        if (count < 1 || count > MAX_SPEAKERS) {
            throw new InvalidVoicePackException("the config has an invalid num_speakers (" + count + ")");
        }
        if (count == 1) {
            return List.of();
        }
        Map<Integer, String> names = new TreeMap<>();
        JsonElement map = settings.get("speaker_id_map");
        if (map != null && map.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : map.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    throw new InvalidVoicePackException("speaker_id_map has a non-numeric id for '" + entry.getKey() + "'");
                }
                int index = value.getAsInt();
                if (index < 0 || index >= count) {
                    throw new InvalidVoicePackException("speaker_id_map id " + index + " is outside 0.." + (count - 1));
                }
                if (names.put(index, clean(entry.getKey())) != null) {
                    throw new InvalidVoicePackException("speaker_id_map uses id " + index + " twice");
                }
            }
        }
        List<VoiceSpeaker> speakers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            speakers.add(new VoiceSpeaker(i, names.get(i)));
        }
        return List.copyOf(speakers);
    }

    private static String clean(String text) {
        String value = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        return value.length() > 60 ? value.substring(0, 60).strip() : value;
    }
}
