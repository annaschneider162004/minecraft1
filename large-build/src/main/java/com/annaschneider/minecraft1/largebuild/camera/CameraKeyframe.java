package com.annaschneider.minecraft1.largebuild.camera;

/**
 * Camera pose at {@code timeSeconds}. Yaw/pitch use Minecraft conventions: yaw 0 looks toward +Z, 90 toward -X;
 * positive pitch looks down.
 */
public record CameraKeyframe(double timeSeconds, double x, double y, double z, float yaw, float pitch) {
}
