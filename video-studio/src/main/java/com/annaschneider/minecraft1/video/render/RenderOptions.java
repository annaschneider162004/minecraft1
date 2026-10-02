package com.annaschneider.minecraft1.video.render;

/** Output format of the rendered MP4 (H.264 video, AAC audio). */
public record RenderOptions(int width, int height, int fps) {
    public RenderOptions {
        width = even(Math.max(320, Math.min(3840, width)));
        height = even(Math.max(180, Math.min(2160, height)));
        fps = Math.max(10, Math.min(60, fps));
    }

    public static RenderOptions hd720() {
        return new RenderOptions(1280, 720, 30);
    }

    public static RenderOptions hd1080() {
        return new RenderOptions(1920, 1080, 30);
    }

    private static int even(int value) {
        return value - (value % 2);
    }
}
