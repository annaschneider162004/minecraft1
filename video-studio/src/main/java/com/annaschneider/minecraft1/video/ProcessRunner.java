package com.annaschneider.minecraft1.video;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Runs external tools (FFmpeg, Piper). Abstracted so the pipeline can be tested without them. */
public interface ProcessRunner {
    /**
     * Runs {@code command} (no shell), writes {@code stdin} (may be {@code null}) and waits at most {@code timeout}.
     *
     * @throws IOException if the program cannot be started or does not finish in time
     */
    Result run(List<String> command, String stdin, Duration timeout) throws IOException, InterruptedException;

    /** Exit code plus the (tail of the) combined stdout/stderr output. */
    record Result(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }

        /** Last non-empty output lines, for error messages. */
        public String tail(int lines) {
            String[] all = output == null ? new String[0] : output.strip().split("\\R");
            int from = Math.max(0, all.length - lines);
            return String.join("\n", java.util.Arrays.copyOfRange(all, from, all.length));
        }
    }
}
