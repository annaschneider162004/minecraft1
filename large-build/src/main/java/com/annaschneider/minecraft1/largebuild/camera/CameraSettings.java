package com.annaschneider.minecraft1.largebuild.camera;

/**
 * Bounded tuning values of the live cinematic camera. Every field is clamped, so a misconfigured value can never make
 * the camera fly away from the build, spin wildly or update faster than the client can follow.
 *
 * @param orbitDistance            extra blocks added to the orbit radius (3..128)
 * @param orbitHeight              extra blocks the camera floats above the build (1..96)
 * @param orbitSpeedDegreesPerSecond angular speed of the orbit (1..90)
 * @param autoShotSeconds          seconds a single shot is held in {@link CameraMode#AUTO} (3..120)
 * @param maxMoveSpeed             blocks per second the camera may travel while catching up (1..64)
 * @param maxDistance              hard cap on the distance between the camera and the framed area (16..512)
 */
public record CameraSettings(
    double orbitDistance,
    double orbitHeight,
    double orbitSpeedDegreesPerSecond,
    double autoShotSeconds,
    double maxMoveSpeed,
    double maxDistance
) {
    public CameraSettings {
        orbitDistance = clamp(orbitDistance, 3, 128);
        orbitHeight = clamp(orbitHeight, 1, 96);
        orbitSpeedDegreesPerSecond = clamp(orbitSpeedDegreesPerSecond, 1, 90);
        autoShotSeconds = clamp(autoShotSeconds, 3, 120);
        maxMoveSpeed = clamp(maxMoveSpeed, 1, 64);
        maxDistance = clamp(maxDistance, 16, 512);
    }

    public static CameraSettings defaults() {
        return new CameraSettings(18, 12, 9, 12, 18, 192);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
