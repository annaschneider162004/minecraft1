package com.annaschneider.minecraft1.video.story;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic reading of a video prompt such as "60 second video: a kingdom rises from a deserted island".
 *
 * @param subject       what the video is about, with length hints removed
 * @param targetSeconds requested length (clamped to {@link #MIN_SECONDS}..{@link #MAX_SECONDS}; default 60)
 * @param language      "vi" when the prompt is written in Vietnamese, otherwise "en"
 * @param fastPaced     whether the prompt asks for a timelapse / fast video
 */
public record PromptAnalysis(String subject, double targetSeconds, String language, boolean fastPaced) {
    public static final double MIN_SECONDS = 15;
    public static final double MAX_SECONDS = 600;
    public static final double DEFAULT_SECONDS = 60;
    public static final int MAX_PROMPT_LENGTH = 2000;

    private static final Pattern SECONDS = Pattern.compile(
        "(\\d{1,4})\\s*(?:s|sec|secs|second|seconds|gi\u00e2y|giay)(?!\\p{L})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MINUTES = Pattern.compile(
        "(\\d{1,3})\\s*(?:m|min|mins|minute|minutes|ph\u00fat|phut)(?!\\p{L})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VIETNAMESE = Pattern.compile(
        "[\u0103\u00e2\u0111\u00ea\u00f4\u01a1\u01b0\u1ea0-\u1ef9]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FAST = Pattern.compile(
        "(?<!\\p{L})(?:timelapse|time-lapse|time lapse|fast|speed|speedup|speed-up|nhanh|tua)(?!\\p{L})",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LEADING_FILLER = Pattern.compile(
        "^(?:(?:a|an|the|make|create|video|clip|film|movie|telling|tell|about|showing|show|of|t\u1ea1o|l\u00e0m|m\u1ed9t|phim"
            + "|k\u1ec3|chuy\u1ec7n|c\u00e2u|v\u1ec1)(?!\\p{L})[\\s,:;.\\-]*)+",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static PromptAnalysis of(String prompt) {
        String text = prompt == null ? "" : prompt.strip();
        if (text.length() > MAX_PROMPT_LENGTH) {
            text = text.substring(0, MAX_PROMPT_LENGTH);
        }
        double seconds = DEFAULT_SECONDS;
        String rest = text;
        Matcher minutes = MINUTES.matcher(text);
        Matcher secs = SECONDS.matcher(text);
        if (secs.find()) {
            seconds = Integer.parseInt(secs.group(1));
            rest = secs.replaceFirst(" ");
        } else if (minutes.find()) {
            seconds = Integer.parseInt(minutes.group(1)) * 60.0;
            rest = minutes.replaceFirst(" ");
        }
        seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
        String language = VIETNAMESE.matcher(text).find() ? "vi" : "en";
        boolean fast = FAST.matcher(text).find();
        String subject = rest.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        subject = LEADING_FILLER.matcher(subject).replaceFirst("");
        subject = subject.replaceAll("^[\\s,:;.\\-]+|[\\s,:;.!\\-]+$", "");
        if (subject.length() > 80) {
            int cut = subject.lastIndexOf(' ', 80);
            subject = subject.substring(0, cut > 30 ? cut : 80).strip();
        }
        return new PromptAnalysis(subject, seconds, language, fast);
    }

    public boolean vietnamese() {
        return language.toLowerCase(Locale.ROOT).startsWith("vi");
    }
}
