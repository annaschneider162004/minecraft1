package com.annaschneider.minecraft1.video.voice;

import com.annaschneider.minecraft1.video.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <a href="https://github.com/rhasspy/piper">Piper</a> neural TTS: runs offline from a {@code piper} executable and
 * {@code .onnx} voice files. Text is passed on standard input (one line), never on the command line.
 */
public final class PiperTtsEngine implements TtsEngine {
    public static final String ID = "piper";
    public static final String ENV_VARIABLE = "ARCHITECT_PIPER";
    public static final String MISSING_MESSAGE = "Piper (local text-to-speech) was not found. Download it from "
        + "https://github.com/rhasspy/piper/releases, unzip it and choose piper.exe in Video Studio > Tools (or put it "
        + "on PATH / set " + ENV_VARIABLE + "). You can still export the video without narration.";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final Path executable;
    private final ProcessRunner runner;

    /** @param executable the piper program, or {@code null} when it was not found */
    public PiperTtsEngine(Path executable, ProcessRunner runner) {
        this.executable = executable;
        this.runner = runner;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<String> unavailableReason() {
        return executable == null || !Files.isRegularFile(executable) ? Optional.of(MISSING_MESSAGE) : Optional.empty();
    }

    @Override
    public boolean supportsSpeakers() {
        return true;
    }

    /** A chosen speaker must exist in the model's own config; single-speaker models accept no speaker. */
    @Override
    public Optional<String> unsupportedReason(VoiceSelection selection) {
        VoiceSpeaker speaker = selection.speaker();
        if (speaker == null) {
            return Optional.empty();
        }
        List<VoiceSpeaker> speakers;
        try {
            speakers = PiperSpeakers.read(selection.voice().config());
        } catch (InvalidVoicePackException ex) {
            return Optional.of("Cannot read the speakers of voice '" + selection.voice().name() + "': " + ex.getMessage());
        }
        if (speakers.isEmpty()) {
            return Optional.of("Voice '" + selection.voice().name() + "' (" + selection.voice().id()
                + ") has a single speaker; speaker " + speaker.index() + " does not exist.");
        }
        if (speaker.index() >= speakers.size()) {
            return Optional.of("Speaker " + speaker.index() + " does not exist in voice '" + selection.voice().name() + "' ("
                + selection.voice().id() + " has " + speakers.size() + " speakers: 0-" + (speakers.size() - 1) + ").");
        }
        return Optional.empty();
    }

    @Override
    public void synthesize(VoicePack voice, String text, Path output) throws NarrationException {
        synthesize(VoiceSelection.of(voice), text, output);
    }

    @Override
    public void synthesize(VoiceSelection selection, String text, Path output) throws NarrationException {
        Optional<String> problem = unavailableReason();
        if (problem.isEmpty()) {
            problem = unsupportedReason(selection);
        }
        if (problem.isPresent()) {
            throw new NarrationException(problem.get());
        }
        VoicePack voice = selection.voice();
        String line = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        if (line.isEmpty()) {
            throw new NarrationException("Nothing to say: the narration text is empty.");
        }
        List<String> command = new ArrayList<>(List.of(executable.toString(), "--model", voice.model().toString(), "--config",
            voice.config().toString()));
        if (selection.speaker() != null) {
            command.addAll(List.of("--speaker", Integer.toString(selection.speaker().index())));
        }
        command.addAll(List.of("--output_file", output.toString()));
        ProcessRunner.Result result;
        try {
            result = runner.run(List.copyOf(command), line + "\n", TIMEOUT);
        } catch (IOException ex) {
            throw new NarrationException("Could not run Piper (" + executable + "): " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new NarrationException("Narration was cancelled", ex);
        }
        try {
            if (!result.ok() || !Files.isRegularFile(output) || Files.size(output) <= 44) {
                throw new NarrationException("Piper could not speak with voice '" + selection.name() + "' (exit code "
                    + result.exitCode() + "): " + result.tail(3));
            }
        } catch (IOException ex) {
            throw new NarrationException("Piper produced no audio: " + ex.getMessage(), ex);
        }
    }
}
