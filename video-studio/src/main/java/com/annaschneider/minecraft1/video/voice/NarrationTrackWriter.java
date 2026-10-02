package com.annaschneider.minecraft1.video.voice;

import com.annaschneider.minecraft1.video.Scene;
import com.annaschneider.minecraft1.video.Storyboard;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Joins the per-scene narration clips into one WAV narration track: each clip starts with its scene and is followed by
 * silence until the scene ends, so the track lines up with the storyboard. Pure Java (no FFmpeg needed).
 */
public final class NarrationTrackWriter {
    private static final byte[] SILENCE = new byte[64 * 1024];

    private NarrationTrackWriter() {
    }

    public static Path write(Storyboard storyboard, Map<Integer, NarrationClip> clips, Path output) throws NarrationException {
        AudioFormat format = null;
        for (Scene scene : storyboard.scenes()) {
            NarrationClip clip = clips.get(scene.index());
            if (clip != null) {
                format = formatOf(clip.wav());
                break;
            }
        }
        if (format == null) {
            throw new NarrationException("There is no narration to save: the story has no spoken text.");
        }
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()) || format.getFrameSize() <= 0) {
            throw new NarrationException("The narration audio format (" + format + ") is not supported; expected signed PCM WAV.");
        }
        int frameSize = format.getFrameSize();
        Path pcm = null;
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            // raw samples go to a temporary file first, so long stories are never held in memory
            pcm = Files.createTempFile(output.toAbsolutePath().getParent(), ".narration-", ".pcm");
            long totalBytes = 0;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(pcm))) {
                for (Scene scene : storyboard.scenes()) {
                    NarrationClip clip = clips.get(scene.index());
                    byte[] data = clip == null ? new byte[0] : read(clip.wav(), format);
                    out.write(data);
                    long sceneBytes = Math.round(scene.durationSeconds() * format.getFrameRate()) * frameSize;
                    long padding = Math.max(0, sceneBytes - data.length);
                    while (padding > 0) {
                        int chunk = (int) Math.min(SILENCE.length, padding);
                        out.write(SILENCE, 0, chunk);
                        padding -= chunk;
                    }
                    totalBytes += Math.max(sceneBytes, data.length);
                }
            }
            try (AudioInputStream track = new AudioInputStream(new BufferedInputStream(Files.newInputStream(pcm)), format,
                totalBytes / frameSize)) {
                AudioSystem.write(track, AudioFileFormat.Type.WAVE, output.toFile());
            }
        } catch (IOException ex) {
            deleteQuietly(output);
            throw new NarrationException("Could not save the narration track " + output.getFileName() + ": " + ex.getMessage(), ex);
        } finally {
            if (pcm != null) {
                deleteQuietly(pcm);
            }
        }
        return output;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // nothing more to do
        }
    }

    private static AudioFormat formatOf(Path wav) throws NarrationException {
        try {
            return AudioSystem.getAudioFileFormat(wav.toFile()).getFormat();
        } catch (UnsupportedAudioFileException | IOException ex) {
            throw new NarrationException("The narration file " + wav.getFileName() + " is not a readable WAV file: " + ex.getMessage(), ex);
        }
    }

    private static byte[] read(Path wav, AudioFormat expected) throws NarrationException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
            if (!in.getFormat().matches(expected)) {
                throw new NarrationException("The narration clips use different audio formats (" + wav.getFileName() + ").");
            }
            byte[] data = in.readAllBytes();
            return data.length % expected.getFrameSize() == 0 ? data
                : java.util.Arrays.copyOf(data, data.length - data.length % expected.getFrameSize());
        } catch (UnsupportedAudioFileException | IOException ex) {
            throw new NarrationException("The narration file " + wav.getFileName() + " is not a readable WAV file: " + ex.getMessage(), ex);
        }
    }
}
