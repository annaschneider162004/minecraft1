package com.annaschneider.minecraft1.video.render;

import com.annaschneider.minecraft1.video.PlannedSegment;
import com.annaschneider.minecraft1.video.VideoExportException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders planned segments to an MP4 with FFmpeg: every segment becomes a uniform H.264 part (trimmed, speed-changed,
 * scaled/letterboxed, faded), the parts are joined without re-encoding, then the narration track (one padded WAV per
 * scene) is mixed in as AAC. All intermediate files live in {@code workDir}.
 */
public final class VideoRenderer {
    static final String TITLE_CARD_COLOR = "0x1d2533";

    private final FfmpegTool ffmpeg;
    private final RenderOptions options;

    public VideoRenderer(FfmpegTool ffmpeg, RenderOptions options) {
        this.ffmpeg = ffmpeg;
        this.options = options;
    }

    /**
     * @param audio per-scene narration in scene order; empty (or all {@code null} narration) renders without narration
     * @return {@code output}
     */
    public Path render(List<PlannedSegment> segments, List<SceneAudio> audio, Path workDir, Path output,
                       java.util.function.Consumer<String> progress) throws VideoExportException {
        if (segments.isEmpty()) {
            throw new VideoExportException("Nothing to render: the story has no scenes.");
        }
        try {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                PlannedSegment segment = segments.get(i);
                String name = String.format(Locale.ROOT, "part-%03d.mp4", i + 1);
                progress.accept(String.format(Locale.ROOT, "Cutting clip %d of %d (scene %d%s)...", i + 1, segments.size(),
                    segment.sceneIndex() + 1, segment.speed() > 1.05 ? String.format(Locale.ROOT, ", %.1fx timelapse", segment.speed()) : ""));
                ffmpeg.run(segmentArguments(segment, workDir.resolve(name)), "cutting clip " + (i + 1));
                parts.add(name);
            }
            Path video = workDir.resolve("video-only.mp4");
            Path partList = writeList(workDir.resolve("parts.txt"), parts);
            progress.accept("Joining clips...");
            ffmpeg.run(List.of("-f", "concat", "-safe", "0", "-i", partList.toString(), "-c", "copy", video.toString()), "joining clips");

            boolean narrated = audio.stream().anyMatch(a -> a.narration() != null);
            Path finished = workDir.resolve("final.mp4");
            if (narrated) {
                List<String> tracks = new ArrayList<>();
                for (SceneAudio scene : audio) {
                    String name = String.format(Locale.ROOT, "scene-audio-%02d.wav", scene.sceneIndex() + 1);
                    ffmpeg.run(sceneAudioArguments(scene, workDir.resolve(name)), "preparing narration for scene " + (scene.sceneIndex() + 1));
                    tracks.add(name);
                }
                Path narration = workDir.resolve("narration.wav");
                Path trackList = writeList(workDir.resolve("narration.txt"), tracks);
                progress.accept("Adding narration...");
                ffmpeg.run(List.of("-f", "concat", "-safe", "0", "-i", trackList.toString(), "-c", "copy", narration.toString()),
                    "joining narration");
                ffmpeg.run(List.of("-i", video.toString(), "-i", narration.toString(), "-map", "0:v:0", "-map", "1:a:0",
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", finished.toString()),
                    "adding narration");
            } else {
                ffmpeg.run(List.of("-i", video.toString(), "-c", "copy", "-movflags", "+faststart", finished.toString()),
                    "finishing the video");
            }
            if (!Files.isRegularFile(finished) || Files.size(finished) == 0) {
                throw new VideoExportException("FFmpeg did not produce a video file.");
            }
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.move(finished, output, StandardCopyOption.REPLACE_EXISTING);
            return output;
        } catch (IOException ex) {
            throw new VideoExportException("Could not write the video: " + ex.getMessage(), ex);
        }
    }

    List<String> segmentArguments(PlannedSegment segment, Path out) {
        List<String> args = new ArrayList<>();
        String outputSeconds = num(segment.outputDuration());
        String frame = String.format(Locale.ROOT, "scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=%d",
            options.width(), options.height(), options.width(), options.height(), options.fps());
        String filter;
        if (segment.isTitleCard()) {
            args.addAll(List.of("-f", "lavfi", "-i", String.format(Locale.ROOT, "color=c=%s:s=%dx%d:r=%d:d=%s",
                TITLE_CARD_COLOR, options.width(), options.height(), options.fps(), outputSeconds)));
            filter = "setsar=1";
        } else {
            args.addAll(List.of("-ss", num(segment.sourceStart()), "-t", num(segment.sourceDuration()), "-i", segment.clip().toString()));
            filter = "setpts=(PTS-STARTPTS)*" + num(segment.outputDuration() / segment.sourceDuration()) + "," + frame
                + ",tpad=stop_mode=clone:stop_duration=" + outputSeconds;
        }
        if (segment.fadeIn() > 0) {
            filter += ",fade=t=in:st=0:d=" + num(segment.fadeIn());
        }
        if (segment.fadeOut() > 0) {
            filter += ",fade=t=out:st=" + num(Math.max(0, segment.outputDuration() - segment.fadeOut())) + ":d=" + num(segment.fadeOut());
        }
        args.addAll(List.of("-vf", filter, "-t", outputSeconds, "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
            "-pix_fmt", "yuv420p", "-r", String.valueOf(options.fps()), out.toString()));
        return args;
    }

    static List<String> sceneAudioArguments(SceneAudio scene, Path out) {
        List<String> args = new ArrayList<>();
        if (scene.narration() != null) {
            args.addAll(List.of("-i", scene.narration().toString(), "-af", "apad"));
        } else {
            args.addAll(List.of("-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"));
        }
        args.addAll(List.of("-t", num(scene.sceneSeconds()), "-ar", "48000", "-ac", "2", "-c:a", "pcm_s16le", out.toString()));
        return args;
    }

    /** FFmpeg concat list with plain file names (resolved next to the list file). */
    private static Path writeList(Path file, List<String> names) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String name : names) {
            text.append("file '").append(name).append("'\n");
        }
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    static String num(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
