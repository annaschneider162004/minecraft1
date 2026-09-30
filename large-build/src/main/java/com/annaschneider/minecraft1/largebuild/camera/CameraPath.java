package com.annaschneider.minecraft1.largebuild.camera;

import java.util.List;

/**
 * Ordered keyframes for one shot. {@link #sample(double)} interpolates positions linearly and angles along the
 * shortest arc.
 */
public record CameraPath(ShotType type, List<CameraKeyframe> keyframes) {
    public CameraPath {
        if (keyframes == null || keyframes.size() < 2) {
            throw new IllegalArgumentException("A camera path needs at least two keyframes.");
        }
        keyframes = List.copyOf(keyframes);
        for (int i = 1; i < keyframes.size(); i++) {
            if (keyframes.get(i).timeSeconds() < keyframes.get(i - 1).timeSeconds()) {
                throw new IllegalArgumentException("Camera keyframes must be ordered by time.");
            }
        }
    }

    public double durationSeconds() {
        return keyframes.get(keyframes.size() - 1).timeSeconds() - keyframes.get(0).timeSeconds();
    }

    public CameraKeyframe sample(double timeSeconds) {
        CameraKeyframe first = keyframes.get(0);
        if (timeSeconds <= first.timeSeconds()) {
            return first;
        }
        for (int i = 1; i < keyframes.size(); i++) {
            CameraKeyframe b = keyframes.get(i);
            if (timeSeconds <= b.timeSeconds()) {
                CameraKeyframe a = keyframes.get(i - 1);
                double span = b.timeSeconds() - a.timeSeconds();
                double t = span <= 0 ? 1 : (timeSeconds - a.timeSeconds()) / span;
                return new CameraKeyframe(timeSeconds, lerp(a.x(), b.x(), t), lerp(a.y(), b.y(), t), lerp(a.z(), b.z(), t),
                    lerpAngle(a.yaw(), b.yaw(), t), (float) lerp(a.pitch(), b.pitch(), t));
            }
        }
        return keyframes.get(keyframes.size() - 1);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, double t) {
        double delta = ((b - a) % 360 + 540) % 360 - 180;
        return (float) (a + delta * t);
    }
}
