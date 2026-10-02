package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.story.LlmClient;
import com.annaschneider.minecraft1.video.story.LocalLlmStoryGenerator;
import com.annaschneider.minecraft1.video.story.OllamaClient;
import com.annaschneider.minecraft1.video.story.PromptAnalysis;
import com.annaschneider.minecraft1.video.story.StoryRequest;
import com.annaschneider.minecraft1.video.story.StoryResult;
import com.annaschneider.minecraft1.video.story.StoryService;
import com.annaschneider.minecraft1.video.story.TemplateStoryGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryGenerationTest {
    private static final BuildContext PALACE = new BuildContext("white-palace",
        List.of(new BuildMilestone(5, 0, "started"), new BuildMilestone(95, 100, "completed")),
        List.of("palace", "waterfall", "bridge"), 1_234_567);

    @Test
    void readsLengthLanguageAndSubjectFromPrompt() {
        PromptAnalysis english = PromptAnalysis.of("Make a 45 second timelapse video of a kingdom rising from a deserted island!");
        assertEquals(45, english.targetSeconds());
        assertEquals("en", english.language());
        assertTrue(english.fastPaced());
        assertEquals("timelapse video of a kingdom rising from a deserted island", english.subject());

        PromptAnalysis vietnamese = PromptAnalysis.of("Video 2 ph\u00fat, k\u1ec3 chuy\u1ec7n m\u1ed9t v\u01b0\u01a1ng qu\u1ed1c m\u1ecdc l\u00ean t\u1eeb \u0111\u1ea3o hoang");
        assertEquals(120, vietnamese.targetSeconds());
        assertEquals("vi", vietnamese.language());
        assertFalse(vietnamese.fastPaced());
        assertEquals("v\u01b0\u01a1ng qu\u1ed1c m\u1ecdc l\u00ean t\u1eeb \u0111\u1ea3o hoang", vietnamese.subject());

        assertEquals(PromptAnalysis.MAX_SECONDS, PromptAnalysis.of("9999 seconds").targetSeconds());
        assertEquals(PromptAnalysis.MIN_SECONDS, PromptAnalysis.of("a 3s clip").targetSeconds());
        assertEquals(PromptAnalysis.DEFAULT_SECONDS, PromptAnalysis.of("castle story").targetSeconds());
    }

    @Test
    void templateStoryIsDeterministicAndFillsTheRequestedLength() {
        TemplateStoryGenerator generator = new TemplateStoryGenerator();
        StoryRequest request = new StoryRequest("60 second video: a white palace above the waterfalls", PALACE, null, null);
        Storyboard first = generator.generate(request);
        Storyboard second = generator.generate(request);
        assertEquals(first, second);
        assertEquals(60, first.totalSeconds(), 0.01);
        assertEquals("template", first.generator());
        assertEquals(List.of(SceneKind.INTRO, SceneKind.PROGRESS, SceneKind.TIMELAPSE, SceneKind.PROGRESS, SceneKind.FINALE),
            first.scenes().stream().map(Scene::kind).toList());
        assertTrue(first.scenes().stream().allMatch(s -> !s.narration().isBlank() && !s.title().isBlank()));
        assertTrue(first.title().contains("white palace above the waterfalls"), first.title());
        assertTrue(first.scenes().get(1).narration().contains("25 percent"));
        assertTrue(first.scenes().get(1).narration().contains("palace"));
        assertTrue(first.scenes().get(4).narration().contains("1,234,567 blocks"));
        Scene timelapse = first.scenes().get(2);
        assertEquals(0.25, timelapse.fromProgress());
        assertEquals(0.75, timelapse.toProgress());
        assertTrue(first.describe().contains("Timelapse"));
    }

    @Test
    void shortAndVietnameseStories() {
        Storyboard shortStory = new TemplateStoryGenerator().generate(new StoryRequest("20 second castle", null, null, null));
        assertEquals(3, shortStory.scenes().size());
        assertEquals(20, shortStory.totalSeconds(), 0.01);
        assertTrue(shortStory.scenes().get(1).isTimelapse());

        Storyboard vietnamese = new TemplateStoryGenerator().generate(new StoryRequest("l\u00e2u \u0111\u00e0i tr\u1eafng", PALACE, 30.0, null));
        assertEquals("vi", vietnamese.language());
        assertTrue(vietnamese.title().startsWith("H\u00e0nh tr\u00ecnh"), vietnamese.title());
        assertTrue(vietnamese.scenes().get(4).narration().contains("1.234.567 kh\u1ed1i"));

        // the voice language wins over the prompt language
        Storyboard forced = new TemplateStoryGenerator().generate(new StoryRequest("white castle", null, null, "vi_VN"));
        assertEquals("vi", forced.language());
    }

    @Test
    void emptyPromptStillProducesAStoryFromTheBuildName() {
        StoryResult result = StoryService.templatesOnly().generate(new StoryRequest("  ", PALACE, null, null));
        assertTrue(result.storyboard().title().contains("white palace"));
        assertEquals(1, result.warnings().size());
    }

    @Test
    void localAiWritesNarrationIntoTheTemplateScenes() {
        LlmClient fake = fake("{\"title\":\"Rise of the Sky Palace\",\"scenes\":[\"One.\",\"Two **bold**.\",{\"narration\":\"Three.\"},\"Four.\",\"Five.\"]}");
        StoryService service = new StoryService(new TemplateStoryGenerator(), new LocalLlmStoryGenerator(fake, new TemplateStoryGenerator()));
        StoryResult result = service.generate(new StoryRequest("sky palace", PALACE, 60.0, null));
        Storyboard story = result.storyboard();
        assertTrue(result.warnings().isEmpty());
        assertEquals("fake-llm", story.generator());
        assertEquals("Rise of the Sky Palace", story.title());
        assertEquals(List.of("One.", "Two bold.", "Three.", "Four.", "Five."), story.scenes().stream().map(Scene::narration).toList());
        assertEquals(60, story.totalSeconds(), 0.01);
    }

    @Test
    void fallsBackToTemplatesWhenTheAiIsMissingOrAnswersBadly() {
        TemplateStoryGenerator templates = new TemplateStoryGenerator();
        StoryRequest request = new StoryRequest("sky palace", PALACE, 60.0, null);
        Storyboard expected = templates.generate(request);
        for (LlmClient broken : List.of(failing(), fake("not json"), fake("{\"scenes\":[\"only one\"]}"), fake("[1,2]"))) {
            StoryResult result = new StoryService(templates, new LocalLlmStoryGenerator(broken, templates)).generate(request);
            assertEquals(expected, result.storyboard());
            assertEquals(1, result.warnings().size());
            assertTrue(result.warnings().get(0).contains("built-in story templates"), result.warnings().get(0));
        }
        // partial answers are completed from the templates
        StoryResult partial = new StoryService(templates, new LocalLlmStoryGenerator(fake("{\"scenes\":[\"A\",\"B\",\"\",\"D\"]}"), templates))
            .generate(request);
        assertEquals("A", partial.storyboard().scenes().get(0).narration());
        assertEquals(expected.scenes().get(2).narration(), partial.storyboard().scenes().get(2).narration());
    }

    @Test
    void ollamaOnlyTalksToThisComputer() {
        assertEquals("ollama:llama3.2", new OllamaClient(null, null).name());
        new OllamaClient("http://localhost:11434", "qwen2.5:3b");
        assertThrows(IllegalArgumentException.class, () -> new OllamaClient("https://api.example.com", "x"));
        assertThrows(IllegalArgumentException.class, () -> new OllamaClient("file:///etc/passwd", "x"));
        assertThrows(IllegalArgumentException.class, () -> new OllamaClient(OllamaClient.DEFAULT_URL, "bad model; rm"));
    }

    @Test
    void unreachableOllamaFallsBackGracefully() {
        // port 9 (discard) is not an Ollama server; the story must still be produced
        TemplateStoryGenerator templates = new TemplateStoryGenerator();
        OllamaClient client = new OllamaClient("http://127.0.0.1:9", "llama3.2", java.time.Duration.ofSeconds(5));
        StoryResult result = new StoryService(templates, new LocalLlmStoryGenerator(client, templates))
            .generate(new StoryRequest("castle", null, null, null));
        assertEquals("template", result.storyboard().generator());
        assertEquals(1, result.warnings().size());
    }

    private static LlmClient fake(String answer) {
        return new LlmClient() {
            @Override
            public String name() {
                return "fake-llm";
            }

            @Override
            public String complete(String prompt) {
                assertTrue(prompt.contains("exactly 5 narration strings") || prompt.contains("narration strings"));
                return answer;
            }
        };
    }

    private static LlmClient failing() {
        return new LlmClient() {
            @Override
            public String name() {
                return "down";
            }

            @Override
            public String complete(String prompt) throws IOException {
                throw new IOException("Ollama is not running");
            }
        };
    }
}
