package com.annaschneider.minecraft1.video.story;

import java.io.IOException;

/** Minimal text-completion backend (a local model server). Implementations must not call cloud services. */
public interface LlmClient {
    /** Short name for messages, e.g. "ollama:llama3.2". */
    String name();

    /** Returns the model's answer to {@code prompt}; it is asked to answer with a JSON object. */
    String complete(String prompt) throws IOException, InterruptedException;
}
