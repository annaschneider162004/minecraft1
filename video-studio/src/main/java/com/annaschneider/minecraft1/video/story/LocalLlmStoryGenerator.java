package com.annaschneider.minecraft1.video.story;

import com.annaschneider.minecraft1.video.BuildContext;
import com.annaschneider.minecraft1.video.Scene;
import com.annaschneider.minecraft1.video.Storyboard;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optional story writer backed by a local language model. The deterministic template generator still decides the
 * scenes (kind, progress, timing); the model only rewrites the title and the narration. Any missing or unusable answer
 * fails with {@link StoryGenerationException} so the caller can fall back to the templates.
 */
public final class LocalLlmStoryGenerator implements StoryGenerator {
    static final int MAX_NARRATION_CHARS = 400;
    static final int MAX_TITLE_CHARS = 100;

    private final LlmClient client;
    private final TemplateStoryGenerator templates;

    public LocalLlmStoryGenerator(LlmClient client, TemplateStoryGenerator templates) {
        this.client = client;
        this.templates = templates;
    }

    @Override
    public String id() {
        return client.name();
    }

    @Override
    public Storyboard generate(StoryRequest request) throws StoryGenerationException {
        Storyboard skeleton = templates.generate(request);
        String answer;
        try {
            answer = client.complete(buildPrompt(request, skeleton));
        } catch (IOException ex) {
            throw new StoryGenerationException(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StoryGenerationException("Story generation was interrupted", ex);
        }
        return merge(skeleton, answer, client.name());
    }

    static String buildPrompt(StoryRequest request, Storyboard skeleton) {
        BuildContext context = request.context();
        String language = skeleton.language().equals("vi") ? "Vietnamese" : "English";
        StringBuilder text = new StringBuilder();
        text.append("You write the voice-over for a short Minecraft video that shows a build being constructed.\n");
        text.append("Video idea from the user: \"").append(request.prompt().replace('"', '\'')).append("\"\n");
        if (!context.buildName().isBlank()) {
            text.append("Build name: ").append(context.buildName()).append('\n');
        }
        if (!context.sections().isEmpty()) {
            text.append("Parts of the build: ").append(String.join(", ", context.sections())).append('\n');
        }
        if (context.blocks() > 0) {
            text.append("Blocks: ").append(context.blocks()).append('\n');
        }
        text.append("Write in ").append(language).append(". Spoken, warm, simple sentences. No emojis, no stage directions.\n");
        text.append("Answer ONLY with JSON: {\"title\": string, \"scenes\": [string, ...]} with exactly ")
            .append(skeleton.scenes().size()).append(" narration strings, one per scene:\n");
        for (Scene scene : skeleton.scenes()) {
            int words = Math.max(6, (int) Math.round(scene.durationSeconds() * 2.2));
            text.append(String.format(Locale.ROOT, "%d. %s - shows the build at %.0f%%%s, about %.0f seconds, at most %d words%n",
                scene.index() + 1, scene.title(), scene.fromProgress() * 100,
                scene.isTimelapse() ? String.format(Locale.ROOT, " to %.0f%% sped up", scene.toProgress() * 100) : "",
                scene.durationSeconds(), words));
        }
        return text.toString();
    }

    static Storyboard merge(Storyboard skeleton, String answer, String generatorName) throws StoryGenerationException {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(answer == null ? "" : answer.strip());
            if (!parsed.isJsonObject()) {
                throw new StoryGenerationException("The local AI did not answer with a JSON object");
            }
            json = parsed.getAsJsonObject();
        } catch (JsonParseException ex) {
            throw new StoryGenerationException("The local AI answer was not valid JSON", ex);
        }
        List<String> lines = new ArrayList<>();
        if (json.has("scenes") && json.get("scenes").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("scenes");
            for (JsonElement element : array) {
                lines.add(clean(textOf(element), MAX_NARRATION_CHARS));
            }
        }
        long usable = lines.stream().limit(skeleton.scenes().size()).filter(line -> !line.isBlank()).count();
        if (usable * 2 < skeleton.scenes().size()) {
            throw new StoryGenerationException("The local AI answer had too few usable scenes (" + usable + " of "
                + skeleton.scenes().size() + ")");
        }
        List<Scene> scenes = new ArrayList<>();
        for (Scene scene : skeleton.scenes()) {
            String line = scene.index() < lines.size() ? lines.get(scene.index()) : "";
            scenes.add(line.isBlank() ? scene : scene.withNarration(line));
        }
        String title = json.has("title") ? clean(textOf(json.get("title")), MAX_TITLE_CHARS) : "";
        return new Storyboard(title.isBlank() ? skeleton.title() : title, skeleton.prompt(), skeleton.language(),
            generatorName, scenes);
    }

    private static String textOf(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : List.of("narration", "text", "voiceover")) {
                if (object.has(key) && object.get(key).isJsonPrimitive()) {
                    return object.get(key).getAsString();
                }
            }
        }
        return "";
    }

    /** Removes control characters and markup-like noise and caps the length at a word boundary. */
    static String clean(String text, int maxChars) {
        String value = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("[*#_`<>\\[\\]{}]", "")
            .replaceAll("\\s+", " ").strip();
        if (value.length() > maxChars) {
            int cut = value.lastIndexOf(' ', maxChars);
            value = value.substring(0, cut > maxChars / 2 ? cut : maxChars).strip();
        }
        return value;
    }
}
