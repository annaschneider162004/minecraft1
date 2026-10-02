package com.annaschneider.minecraft1.video.render;

import com.annaschneider.minecraft1.video.Scene;
import com.annaschneider.minecraft1.video.Storyboard;

import java.util.Locale;

/** Writes the narration as SubRip ({@code .srt}) subtitles that players load next to the MP4. */
public final class SubtitleWriter {
    private static final int LINE_WIDTH = 42;

    private SubtitleWriter() {
    }

    public static String toSrt(Storyboard storyboard) {
        StringBuilder srt = new StringBuilder();
        double start = 0;
        int number = 1;
        for (Scene scene : storyboard.scenes()) {
            double end = start + scene.durationSeconds();
            if (!scene.narration().isBlank()) {
                srt.append(number++).append('\n')
                    .append(time(start + 0.2)).append(" --> ").append(time(Math.max(start + 0.4, end - 0.2))).append('\n')
                    .append(wrap(scene.narration())).append("\n\n");
            }
            start = end;
        }
        return srt.toString();
    }

    static String time(double seconds) {
        long millis = Math.round(seconds * 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", millis / 3_600_000, millis / 60_000 % 60, millis / 1000 % 60,
            millis % 1000);
    }

    private static String wrap(String text) {
        StringBuilder out = new StringBuilder();
        int lineLength = 0;
        for (String word : text.strip().split("\\s+")) {
            if (lineLength > 0 && lineLength + 1 + word.length() > LINE_WIDTH) {
                out.append('\n');
                lineLength = 0;
            } else if (lineLength > 0) {
                out.append(' ');
                lineLength++;
            }
            out.append(word);
            lineLength += word.length();
        }
        return out.toString();
    }
}
