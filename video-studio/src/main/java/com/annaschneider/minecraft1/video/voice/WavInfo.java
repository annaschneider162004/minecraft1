package com.annaschneider.minecraft1.video.voice;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;

/** Reads the length of a WAV file. */
public final class WavInfo {
    private WavInfo() {
    }

    public static double seconds(Path wav) throws NarrationException {
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(wav.toFile());
            float rate = format.getFormat().getFrameRate();
            long frames = format.getFrameLength();
            if (rate > 0 && frames > 0) {
                return frames / (double) rate;
            }
            int frameSize = format.getFormat().getFrameSize();
            long bytes = java.nio.file.Files.size(wav) - 44;
            if (rate > 0 && frameSize > 0 && bytes > 0) {
                return bytes / (double) frameSize / rate;
            }
            throw new NarrationException("The narration file " + wav.getFileName() + " has no audio.");
        } catch (UnsupportedAudioFileException | IOException ex) {
            throw new NarrationException("The narration file " + wav.getFileName() + " is not a readable WAV file: " + ex.getMessage(), ex);
        }
    }
}
