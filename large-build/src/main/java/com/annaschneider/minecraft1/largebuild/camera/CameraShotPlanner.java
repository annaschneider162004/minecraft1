package com.annaschneider.minecraft1.largebuild.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

/**
 * Produces camera paths for a target area. A future AI director can implement this to pick more cinematic shots.
 */
public interface CameraShotPlanner {
    CameraPath plan(ShotType type, Bounds target, double durationSeconds);
}
