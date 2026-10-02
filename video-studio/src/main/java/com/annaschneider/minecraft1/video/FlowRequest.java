package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.render.RenderOptions;
import com.annaschneider.minecraft1.video.voice.VoicePack;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What an {@link ExportFlow} should do.
 *
 * @param voice         narration voice; required when the mode narrates
 * @param recordSeconds how long to record; {@code <= 0} records as long as the story (capped at
 *                      {@link ExportFlow#MAX_RECORD_SECONDS})
 * @param baseName      file name stem for the outputs ({@code null} = story title); a timestamp is appended
 */
public record FlowRequest(ExportMode mode, Storyboard storyboard, BuildContext context, VoicePack voice, double recordSeconds,
                          Path outputFolder, String baseName, RenderOptions options) {
    public FlowRequest {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(storyboard, "storyboard");
        Objects.requireNonNull(outputFolder, "outputFolder");
        context = context == null ? BuildContext.empty() : context;
        options = options == null ? RenderOptions.hd720() : options;
        double seconds = recordSeconds > 0 ? recordSeconds : Math.ceil(storyboard.totalSeconds());
        recordSeconds = Math.max(1, Math.min(ExportFlow.MAX_RECORD_SECONDS, seconds));
    }
}
