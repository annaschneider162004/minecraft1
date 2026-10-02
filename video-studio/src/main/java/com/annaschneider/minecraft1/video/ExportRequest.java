package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.RenderOptions;
import com.annaschneider.minecraft1.video.voice.VoicePack;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What to export.
 *
 * @param footage            recorded video files in playback order (may be empty: title cards are used instead)
 * @param voice              narration voice, or {@code null} for a video without narration
 * @param baseName           file name stem for the outputs; a timestamp is appended
 * @param keepTemporaryFiles keep the intermediate clips/audio (for troubleshooting)
 */
public record ExportRequest(Storyboard storyboard, BuildContext context, List<Path> footage, VoicePack voice, Path outputFolder,
                            String baseName, RenderOptions options, boolean keepTemporaryFiles) {
    public ExportRequest {
        Objects.requireNonNull(storyboard, "storyboard");
        Objects.requireNonNull(outputFolder, "outputFolder");
        context = context == null ? BuildContext.empty() : context;
        footage = footage == null ? List.of() : List.copyOf(footage);
        options = options == null ? RenderOptions.hd720() : options;
    }
}
