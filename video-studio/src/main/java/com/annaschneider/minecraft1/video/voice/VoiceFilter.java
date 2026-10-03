package com.annaschneider.minecraft1.video.voice;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Catalog filter: free-text search plus language, engine, install state and multi-speaker filters.
 *
 * @param query            words that must all appear (name, id, speaker, language, engine, source); blank = any
 * @param language         language code ({@code en_US}) or family ({@code en}); blank = any
 * @param engine           engine id; blank = any
 * @param state            install state
 * @param multiSpeakerOnly only speakers of multi-speaker models
 */
public record VoiceFilter(String query, String language, String engine, State state, boolean multiSpeakerOnly) {
    public enum State {
        ALL("All voices"),
        INSTALLED("Installed"),
        DOWNLOADABLE("Downloadable"),
        CLONED("Cloned");

        private final String label;

        State(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final VoiceFilter ALL = new VoiceFilter("", "", "", State.ALL, false);

    public VoiceFilter {
        query = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        language = language == null ? "" : language.strip();
        engine = engine == null ? "" : engine.strip().toLowerCase(Locale.ROOT);
        state = state == null ? State.ALL : state;
    }

    List<String> words() {
        return query.isEmpty() ? List.of() : Arrays.asList(query.split("\\s+"));
    }

    public boolean matches(VoiceCatalogEntry entry) {
        return matches(entry, words());
    }

    boolean matches(VoiceCatalogEntry entry, List<String> words) {
        boolean stateOk = switch (state) {
            case ALL -> true;
            case INSTALLED -> entry.installed();
            case DOWNLOADABLE -> entry.downloadable();
            case CLONED -> entry.cloned();
        };
        if (!stateOk || (multiSpeakerOnly && !entry.multiSpeaker())) {
            return false;
        }
        if (!engine.isEmpty() && !engine.equals(entry.engine())) {
            return false;
        }
        if (!language.isEmpty() && !sameLanguage(entry.language(), language)) {
            return false;
        }
        for (String word : words) {
            if (!entry.searchText().contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameLanguage(String entry, String wanted) {
        String a = entry.replace('-', '_').toLowerCase(Locale.ROOT);
        String b = wanted.replace('-', '_').toLowerCase(Locale.ROOT);
        return a.equals(b) || a.startsWith(b + "_");
    }
}
