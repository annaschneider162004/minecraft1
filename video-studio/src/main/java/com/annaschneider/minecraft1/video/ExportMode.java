package com.annaschneider.minecraft1.video;

/** What {@link ExportFlow} produces. */
public enum ExportMode {
    /** Capture gameplay footage only and save the raw video (no narration, no muxing). */
    RECORD_ONLY("Record only", true, false),
    /** Generate the local narration audio (plus script and subtitles) from the story only; nothing is recorded. */
    NARRATE_ONLY("Narrate only", false, true),
    /** Record and narrate in parallel, then mux and export the final MP4 automatically once both are finished. */
    AUTO_EXPORT("Auto-export when both complete", true, true);

    private final String label;
    private final boolean records;
    private final boolean narrates;

    ExportMode(String label, boolean records, boolean narrates) {
        this.label = label;
        this.records = records;
        this.narrates = narrates;
    }

    public String label() {
        return label;
    }

    public boolean records() {
        return records;
    }

    public boolean narrates() {
        return narrates;
    }

    @Override
    public String toString() {
        return label;
    }
}
