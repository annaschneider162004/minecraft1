package com.annaschneider.minecraft1.video;

import java.nio.file.Path;
import java.util.List;

/**
 * Outcome of an {@link ExportFlow}. Files that were finished before a failure are kept and listed here.
 *
 * @param stage      {@link FlowStage#COMPLETE}, {@link FlowStage#FAILED} or {@link FlowStage#CANCELLED}
 * @param failure    why it failed, or {@code null}
 * @param video      final narrated MP4 (auto-export), or {@code null}
 * @param recording  raw recorded video, or {@code null}
 * @param narration  narration track (WAV), or {@code null}
 * @param script     story text, or {@code null}
 * @param subtitles  {@code .srt}, or {@code null}
 */
public record FlowResult(ExportMode mode, FlowStage stage, FlowFailure failure, Path video, Path recording, Path narration,
                         Path script, Path subtitles, List<String> warnings) {
    public FlowResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean ok() {
        return stage == FlowStage.COMPLETE;
    }

    /** The main file of the mode: the final video, the raw recording or the narration track. */
    public Path output() {
        if (video != null) {
            return video;
        }
        return mode == ExportMode.NARRATE_ONLY ? narration : recording;
    }
}
