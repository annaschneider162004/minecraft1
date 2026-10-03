package com.annaschneider.minecraft1.video.voice;

import java.util.Locale;
import java.util.Objects;

/**
 * One selectable row of the unified voice catalog: an installed single-speaker pack, one speaker of a multi-speaker
 * model, a cloned profile, or a verified downloadable model/speaker that is not installed yet. The {@link #id()} is
 * stable: the model id, or {@code <model id>#speaker-<index>} for one speaker of a multi-speaker model.
 */
public final class VoiceCatalogEntry {
    /** Where an entry comes from. */
    public enum Source {
        INSTALLED_PACK("installed pack"),
        CLONED("cloned voice"),
        CATALOG("verified catalog");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String id;
    private final String modelId;
    private final String name;
    private final String language;
    private final String engine;
    private final Source source;
    private final String description;
    private final VoiceSpeaker speaker;
    private final int speakerCount;
    private final VoicePack pack;
    private final DownloadableVoice download;
    private final String searchText;

    VoiceCatalogEntry(String modelId, String name, String language, String engine, Source source, String description,
                      VoiceSpeaker speaker, int speakerCount, VoicePack pack, DownloadableVoice download) {
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.id = speaker == null ? modelId : VoiceSelection.speakerId(modelId, speaker.index());
        this.name = name;
        this.language = language;
        this.engine = engine;
        this.source = source;
        this.description = description == null ? "" : description;
        this.speaker = speaker;
        this.speakerCount = Math.max(1, speakerCount);
        this.pack = pack;
        this.download = download;
        this.searchText = String.join(" ", id, name, language, engine, source.label(),
                speaker == null ? "" : speaker.name(), download == null ? "" : download.quality(), this.description)
            .toLowerCase(Locale.ROOT);
    }

    public String id() {
        return id;
    }

    public String modelId() {
        return modelId;
    }

    public String name() {
        return name;
    }

    public String language() {
        return language;
    }

    public String engine() {
        return engine;
    }

    public Source source() {
        return source;
    }

    public String description() {
        return description;
    }

    /** The speaker of a multi-speaker model, or {@code null}. */
    public VoiceSpeaker speaker() {
        return speaker;
    }

    /** Number of speakers of the whole model (1 for single-speaker voices). */
    public int speakerCount() {
        return speakerCount;
    }

    /** The installed files, or {@code null} when the voice must be installed first. */
    public VoicePack pack() {
        return pack;
    }

    /** Verified catalog metadata for this model, or {@code null} for voices that are not in the catalog. */
    public DownloadableVoice download() {
        return download;
    }

    public boolean installed() {
        return pack != null;
    }

    /** Not installed, but can be installed from the verified catalog on request. */
    public boolean downloadable() {
        return pack == null && download != null;
    }

    public boolean multiSpeaker() {
        return speakerCount > 1;
    }

    public boolean cloned() {
        return source == Source.CLONED;
    }

    String searchText() {
        return searchText;
    }

    /** The exact voice to speak with. */
    public VoiceSelection selection() throws NarrationException {
        if (pack == null) {
            throw new NarrationException("Voice '" + displayName() + "' is not installed yet. Select it and click Install voice "
                + "to download it" + (download == null ? "" : " (" + megabytes(download.totalBytes()) + ")") + " first.");
        }
        return speaker == null ? VoiceSelection.of(pack) : VoiceSelection.of(pack, speaker);
    }

    /** Name plus speaker, e.g. {@code Arctic medium - awb}. */
    public String displayName() {
        return speaker == null ? name : name + " - " + speaker.name();
    }

    /** One list row: name, speaker, language, engine and state. */
    public String label() {
        String speakerPart = speaker == null ? "" : String.format(Locale.ROOT, " (speaker %d/%d)", speaker.index() + 1, speakerCount);
        String state = installed() ? (cloned() ? "cloned" : "installed") : "downloadable, " + megabytes(download.totalBytes());
        return displayName() + speakerPart + " - " + language + ", " + engine + " [" + state + "]";
    }

    public static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return label();
    }
}
