package com.annaschneider.minecraft1.video;

import java.nio.file.Path;
import java.util.List;

/**
 * Files written by an export.
 *
 * @param storyboard the story as rendered (scene lengths include any growth to fit the narration)
 * @param video     the MP4
 * @param subtitles narration as {@code .srt} next to the video, or {@code null} when there is no narration text
 * @param script    the storyboard as text
 * @param narrated  whether a voice track was added
 * @param warnings  things the user should know (skipped footage, narration fallback, ...)
 */
public record ExportResult(Storyboard storyboard, Path video, Path subtitles, Path script, boolean narrated, List<String> warnings) {
    public ExportResult {
        warnings = List.copyOf(warnings);
    }
}
