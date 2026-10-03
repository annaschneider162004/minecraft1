package com.annaschneider.minecraft1.video.voice;

import com.annaschneider.minecraft1.video.ProcessRunner;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Optional Coqui XTTS v2 CLI with a preinstalled local model and reference-audio conditioning. */
public final class XttsTtsEngine implements TtsEngine {
    public static final String ID = "xtts";
    public static final String ENV_VARIABLE = "ARCHITECT_XTTS";
    public static final String UNAVAILABLE = "Voice cloning unavailable / Clone voice không khả dụng: "
        + "choose a Coqui tts executable and a local XTTS v2 model folder in Tools. "
        + "You can still use Piper voices or export without narration.";
    private final Path executable;
    private final Path modelFolder;
    private final ProcessRunner runner;

    public XttsTtsEngine(Path executable, Path modelFolder, ProcessRunner runner) {
        this.executable = executable;
        this.modelFolder = modelFolder;
        this.runner = runner;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<String> unavailableReason() {
        if (executable == null || !Files.isRegularFile(executable) || modelFolder == null
            || !Files.isRegularFile(modelFolder.resolve("model.pth"))
            || !Files.isRegularFile(modelFolder.resolve("config.json"))
            || !Files.isRegularFile(modelFolder.resolve("vocab.json"))) {
            return Optional.of(UNAVAILABLE);
        }
        return Optional.empty();
    }

    /** Cloned profiles have no speakers and are English only; anything else is refused up front. */
    @Override
    public Optional<String> unsupportedReason(VoiceSelection selection) {
        Optional<String> speaker = TtsEngine.super.unsupportedReason(selection);
        if (speaker.isPresent()) {
            return speaker;
        }
        if (!"en".equals(selection.language())) {
            return Optional.of("This cloning backend currently supports English profiles only (voice '" + selection.name()
                + "' is '" + selection.language() + "').");
        }
        return Optional.empty();
    }

    @Override
    public void synthesize(VoicePack voice, String text, Path output) throws NarrationException {
        Optional<String> problem = unavailableReason();
        if (problem.isPresent()) {
            throw new NarrationException(problem.get());
        }
        if (!ID.equals(voice.engine()) || !"en".equals(voice.language())) {
            throw new NarrationException("This cloning backend currently supports English profiles only.");
        }
        ClonedVoiceProfiles.validateSample(voice.model());
        String line = text == null ? "" : text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").strip();
        if (line.isEmpty()) {
            throw new NarrationException("Nothing to say: the narration text is empty.");
        }
        // XTTS's custom-model CLI expects a checkpoint directory, not the model.pth file.
        List<String> command = List.of(executable.toString(), "--model_path", modelFolder.toAbsolutePath().toString(),
            "--config_path", modelFolder.resolve("config.json").toAbsolutePath().toString(),
            "--speaker_wav", voice.model().toAbsolutePath().toString(), "--language_idx", "en",
            "--device", "cpu", "--text", line, "--out_path", output.toAbsolutePath().toString());
        try {
            Files.deleteIfExists(output);
            ProcessRunner.Result result = runner.run(command, null, Duration.ofMinutes(10));
            if (!result.ok() || !Files.isRegularFile(output) || Files.size(output) <= 44
                || Files.size(output) > 100L * 1024 * 1024) {
                throw new NarrationException("XTTS could not speak with voice '" + voice.name() + "' (exit code "
                    + result.exitCode() + "): " + result.tail(3));
            }
            correctSampleRate(output);
            WavInfo.seconds(output);
        } catch (IOException | UnsupportedAudioFileException ex) {
            throw new NarrationException("Voice cloning failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new NarrationException("Voice cloning was cancelled", ex);
        }
    }

    /** The custom-model Coqui CLI may label XTTS's 24 kHz samples as its 22.05 kHz input rate. */
    private static void correctSampleRate(Path output) throws IOException, UnsupportedAudioFileException {
        Path corrected = Files.createTempFile("architect-xtts-", ".wav");
        try {
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(output.toFile())) {
                AudioFormat format = audio.getFormat();
                if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                    || format.getSampleSizeInBits() != 16 || format.getChannels() != 1 || format.isBigEndian()) {
                    throw new IOException("XTTS produced an unsupported audio format.");
                }
                AudioFormat rate = new AudioFormat(24000, 16, 1, true, false);
                try (AudioInputStream fixed = new AudioInputStream(audio, rate, audio.getFrameLength())) {
                    AudioSystem.write(fixed, AudioFileFormat.Type.WAVE, corrected.toFile());
                }
            }
            Files.move(corrected, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(corrected);
        }
    }
}
