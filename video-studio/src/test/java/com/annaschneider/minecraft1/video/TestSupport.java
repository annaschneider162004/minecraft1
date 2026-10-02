package com.annaschneider.minecraft1.video;

import com.annaschneider.minecraft1.video.voice.NarrationException;
import com.annaschneider.minecraft1.video.voice.TtsEngine;
import com.annaschneider.minecraft1.video.voice.VoicePack;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Fakes for FFmpeg/Piper so the pipeline can be tested without the real programs. */
final class TestSupport {
    private TestSupport() {
    }

    /** Writes a silent 16-bit mono WAV of the given length. */
    static Path writeWav(Path file, double seconds) throws IOException {
        AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
        int frames = (int) Math.round(seconds * 22050);
        byte[] data = new byte[frames * 2];
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(data), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }

    /** Pretends to be ffmpeg: answers probes with a fixed duration and creates every output file. */
    static final class FakeFfmpeg implements ProcessRunner {
        final List<List<String>> commands = new ArrayList<>();
        double footageSeconds = 100;
        boolean failRendering;

        @Override
        public Result run(List<String> command, String stdin, Duration timeout) throws IOException {
            commands.add(List.copyOf(command));
            if (command.size() == 4 && command.get(2).equals("-i")) {
                long whole = (long) footageSeconds;
                String duration = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d.00", whole / 3600, whole / 60 % 60, whole % 60);
                return new Result(1, "Input #0, mov,mp4\n  Duration: " + duration + ", start: 0.000000, bitrate: 900 kb/s\n"
                    + "At least one output file must be specified");
            }
            if (failRendering) {
                return new Result(1, "Unknown encoder 'libx264'");
            }
            Path out = Path.of(command.get(command.size() - 1));
            Files.writeString(out, "fake media", StandardCharsets.UTF_8);
            return new Result(0, "");
        }

        List<List<String>> renderCommands() {
            return commands.stream().filter(c -> c.contains("-y")).toList();
        }
    }

    /** TTS engine that writes silent WAVs of {@code secondsPerWord} per word. */
    static final class FakeTts implements TtsEngine {
        final List<String> spoken = new ArrayList<>();
        String unavailable;
        double secondsPerWord = 0.4;

        @Override
        public String id() {
            return "piper";
        }

        @Override
        public Optional<String> unavailableReason() {
            return Optional.ofNullable(unavailable);
        }

        @Override
        public void synthesize(VoicePack voice, String text, Path output) throws NarrationException {
            spoken.add(text);
            try {
                writeWav(output, text.split("\\s+").length * secondsPerWord);
            } catch (IOException ex) {
                throw new NarrationException(ex.getMessage(), ex);
            }
        }
    }

    static VoicePack voice(Path folder) {
        return new VoicePack("en_US-test-medium", "Test", "en_US", "piper", folder.resolve("en_US-test-medium.onnx"),
            folder.resolve("en_US-test-medium.onnx.json"), 22050, "");
    }
}
