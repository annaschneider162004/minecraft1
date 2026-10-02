package com.annaschneider.minecraft1.video.story;

import com.annaschneider.minecraft1.video.BuildContext;
import com.annaschneider.minecraft1.video.Scene;
import com.annaschneider.minecraft1.video.SceneKind;
import com.annaschneider.minecraft1.video.Storyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic story writer: always available, needs no AI backend and gives the same story for the same prompt and
 * build. It also provides the scene layout (kinds, progress points, durations) that the optional AI writer fills in.
 */
public final class TemplateStoryGenerator implements StoryGenerator {
    public static final String ID = "template";

    private static final String[] INTRO_EN = {
        "This is the story of %s. It begins with an empty stretch of land and a plan.",
        "Every great build starts small. Today we watch %s rise, block by block.",
        "Picture an empty world. In a few moments, %s will stand right here.",
    };
    private static final String[] INTRO_VI = {
        "\u0110\u00e2y l\u00e0 c\u00e2u chuy\u1ec7n v\u1ec1 %s. M\u1ecdi th\u1ee9 b\u1eaft \u0111\u1ea7u t\u1eeb m\u1ed9t kho\u1ea3ng \u0111\u1ea5t tr\u1ed1ng v\u00e0 m\u1ed9t b\u1ea3n k\u1ebf ho\u1ea1ch.",
        "C\u00f4ng tr\u00ecnh l\u1edbn n\u00e0o c\u0169ng b\u1eaft \u0111\u1ea7u t\u1eeb nh\u1eefng kh\u1ed1i \u0111\u1ea7u ti\u00ean. H\u00f4m nay ch\u00fang ta c\u00f9ng xem %s d\u1ea7n th\u00e0nh h\u00ecnh.",
        "H\u00e3y t\u01b0\u1edfng t\u01b0\u1ee3ng m\u1ed9t th\u1ebf gi\u1edbi tr\u1ed1ng tr\u01a1n. Ch\u1ec9 l\u00e1t n\u1eefa th\u00f4i, %s s\u1ebd \u0111\u1ee9ng s\u1eebng s\u1eefng t\u1ea1i \u0111\u00e2y.",
    };
    private static final String[] PROGRESS_EN = {
        "%d percent done. %s",
        "At %d percent, the builders keep going. %s",
    };
    private static final String[] PROGRESS_VI = {
        "\u0110\u00e3 ho\u00e0n th\u00e0nh %d ph\u1ea7n tr\u0103m. %s",
        "Ti\u1ebfn \u0111\u1ed9 %d ph\u1ea7n tr\u0103m, nh\u1eefng ng\u01b0\u1eddi th\u1ee3 v\u1eabn mi\u1ec7t m\u00e0i l\u00e0m vi\u1ec7c. %s",
    };
    private static final String[] TIMELAPSE_EN = {
        "Now let's speed things up and watch the whole build come together.",
        "Time flies. Block after block, the shape of the build appears.",
    };
    private static final String[] TIMELAPSE_VI = {
        "Gi\u1edd h\u00e3y tua nhanh \u0111\u1ec3 xem to\u00e0n b\u1ed9 c\u00f4ng tr\u00ecnh \u0111\u01b0\u1ee3c d\u1ef1ng l\u00ean.",
        "Th\u1eddi gian tr\u00f4i nhanh. T\u1eebng kh\u1ed1i n\u1ed1i ti\u1ebfp t\u1eebng kh\u1ed1i, h\u00ecnh d\u00e1ng c\u00f4ng tr\u00ecnh d\u1ea7n hi\u1ec7n ra.",
    };
    private static final String[] FINALE_EN = {
        "And here it is: %s%s. Thanks for watching.",
        "The work is done: %s%s, finished at last.",
    };
    private static final String[] FINALE_VI = {
        "V\u00e0 \u0111\u00e2y l\u00e0 th\u00e0nh qu\u1ea3: %s%s. C\u1ea3m \u01a1n b\u1ea1n \u0111\u00e3 theo d\u00f5i.",
        "C\u00f4ng vi\u1ec7c \u0111\u00e3 xong: %s%s, cu\u1ed1i c\u00f9ng \u0111\u00e3 ho\u00e0n th\u00e0nh.",
    };

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Storyboard generate(StoryRequest request) {
        PromptAnalysis analysis = PromptAnalysis.of(request.prompt());
        double target = request.targetSeconds() != null
            ? Math.max(PromptAnalysis.MIN_SECONDS, Math.min(PromptAnalysis.MAX_SECONDS, request.targetSeconds()))
            : analysis.targetSeconds();
        String language = request.language() != null && !request.language().isBlank()
            ? normalizeLanguage(request.language()) : analysis.language();
        boolean vi = language.equals("vi");
        BuildContext context = request.context();
        String subject = subject(analysis, context, vi);
        int variant = Math.floorMod((request.prompt() + "|" + context.buildName()).hashCode(), 6);

        List<Scene> scenes = layout(target, analysis.fastPaced());
        List<Scene> written = new ArrayList<>();
        int progressCount = 0;
        for (Scene scene : scenes) {
            String narration = switch (scene.kind()) {
                case INTRO -> String.format(pick(vi ? INTRO_VI : INTRO_EN, variant), subject);
                case PROGRESS -> {
                    String section = sectionPhrase(context, progressCount++, vi);
                    yield String.format(Locale.ROOT, pick(vi ? PROGRESS_VI : PROGRESS_EN, variant + progressCount),
                        Math.round(scene.fromProgress() * 100), section).strip();
                }
                case TIMELAPSE -> pick(vi ? TIMELAPSE_VI : TIMELAPSE_EN, variant);
                case FINALE -> String.format(pick(vi ? FINALE_VI : FINALE_EN, variant), capitalize(subject),
                    blocksPhrase(context, vi));
            };
            written.add(scene.withNarration(narration).withTitle(sceneTitle(scene, vi)));
        }
        String title = vi ? "H\u00e0nh tr\u00ecnh x\u00e2y d\u1ef1ng " + subject : "The making of " + subject;
        return new Storyboard(capitalize(title), request.prompt(), language, ID, written);
    }

    /**
     * Scene layout for a target length: intro at 0 %, (progress at 25 %), timelapse, (progress at 75 %), finale at 100 %.
     * Durations are proportional and add up to {@code targetSeconds}.
     */
    static List<Scene> layout(double targetSeconds, boolean fastPaced) {
        List<Scene> scenes = new ArrayList<>();
        double timelapseWeight = fastPaced ? 0.45 : 0.28;
        if (targetSeconds < 30) {
            scenes.add(new Scene(0, SceneKind.INTRO, "", "", 0, 0, 0.25));
            scenes.add(new Scene(1, SceneKind.TIMELAPSE, "", "", 0, 1, timelapseWeight + 0.25));
            scenes.add(new Scene(2, SceneKind.FINALE, "", "", 1, 1, 0.25));
        } else {
            double progressWeight = fastPaced ? 0.12 : 0.18;
            scenes.add(new Scene(0, SceneKind.INTRO, "", "", 0, 0, 0.16));
            scenes.add(new Scene(1, SceneKind.PROGRESS, "", "", 0.25, 0.25, progressWeight));
            scenes.add(new Scene(2, SceneKind.TIMELAPSE, "", "", 0.25, 0.75, timelapseWeight));
            scenes.add(new Scene(3, SceneKind.PROGRESS, "", "", 0.75, 0.75, progressWeight));
            scenes.add(new Scene(4, SceneKind.FINALE, "", "", 1, 1, 0.2));
        }
        double weights = scenes.stream().mapToDouble(Scene::durationSeconds).sum();
        List<Scene> scaled = new ArrayList<>();
        double used = 0;
        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            double seconds = i == scenes.size() - 1
                ? Math.round((targetSeconds - used) * 10) / 10.0
                : Math.round(scene.durationSeconds() / weights * targetSeconds * 10) / 10.0;
            used += seconds;
            scaled.add(scene.withDuration(Math.max(1, seconds)));
        }
        return scaled;
    }

    static String normalizeLanguage(String language) {
        String code = language.strip().toLowerCase(Locale.ROOT);
        return code.startsWith("vi") ? "vi" : "en";
    }

    private static String sceneTitle(Scene scene, boolean vi) {
        int percent = (int) Math.round(scene.fromProgress() * 100);
        return switch (scene.kind()) {
            case INTRO -> vi ? "M\u1edf \u0111\u1ea7u" : "Opening";
            case PROGRESS -> (vi ? "Ti\u1ebfn \u0111\u1ed9 " : "Progress ") + percent + "%";
            case TIMELAPSE -> vi ? "Tua nhanh" : "Timelapse";
            case FINALE -> vi ? "Ho\u00e0n th\u00e0nh" : "Finale";
        };
    }

    private static String subject(PromptAnalysis analysis, BuildContext context, boolean vi) {
        if (!analysis.subject().isBlank()) {
            return analysis.subject();
        }
        if (!context.buildName().isBlank()) {
            return context.buildName().replace('-', ' ').replace('_', ' ');
        }
        return vi ? "c\u00f4ng tr\u00ecnh n\u00e0y" : "this build";
    }

    private static String sectionPhrase(BuildContext context, int index, boolean vi) {
        if (context.sections().isEmpty()) {
            return "";
        }
        String section = context.sections().get(index % context.sections().size()).replace('_', ' ').toLowerCase(Locale.ROOT);
        return vi ? "Ph\u1ea7n " + section + " \u0111ang d\u1ea7n hi\u1ec7n ra." : "The " + section + " is taking shape.";
    }

    private static String blocksPhrase(BuildContext context, boolean vi) {
        if (context.blocks() <= 0) {
            return "";
        }
        String count = String.format(Locale.ROOT, "%,d", context.blocks());
        return vi ? ", \u0111\u01b0\u1ee3c x\u00e2y t\u1eeb " + count.replace(',', '.') + " kh\u1ed1i" : ", built from " + count + " blocks";
    }

    private static String pick(String[] options, int variant) {
        return options[Math.floorMod(variant, options.length)];
    }

    static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
