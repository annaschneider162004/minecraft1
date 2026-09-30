package com.annaschneider.minecraft1.largebuild.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-math shot planner: orbit, diagonal fly-by, top-down and rising reveal around a bounding box.
 */
public final class DeterministicShotPlanner implements CameraShotPlanner {
    private static final int ORBIT_STEPS = 24;
    private static final int LINE_STEPS = 12;

    @Override
    public CameraPath plan(ShotType type, Bounds target, double durationSeconds) {
        if (!(durationSeconds > 0) || durationSeconds > 3600) {
            throw new IllegalArgumentException("Shot duration must be in (0, 3600] seconds.");
        }
        double cx = (target.minX() + target.maxX() + 1) / 2.0;
        double cy = (target.minY() + target.maxY() + 1) / 2.0;
        double cz = (target.minZ() + target.maxZ() + 1) / 2.0;
        double span = Math.max(target.sizeX(), target.sizeZ());
        double radius = span * 0.75 + 16;
        double top = target.maxY() + 1;
        List<CameraKeyframe> frames = new ArrayList<>();
        switch (type) {
            case ORBIT -> {
                double height = cy + target.sizeY() * 0.4 + 10;
                for (int i = 0; i <= ORBIT_STEPS; i++) {
                    double angle = 2 * Math.PI * i / ORBIT_STEPS;
                    frames.add(lookAt(durationSeconds * i / ORBIT_STEPS, cx + Math.cos(angle) * radius, height,
                        cz + Math.sin(angle) * radius, cx, cy, cz));
                }
            }
            case FLYBY -> {
                double margin = span * 0.25 + 16;
                for (int i = 0; i <= LINE_STEPS; i++) {
                    double t = (double) i / LINE_STEPS;
                    double x = target.minX() - margin + t * (target.sizeX() + 2 * margin);
                    double z = target.maxZ() + margin - t * (target.sizeZ() + 2 * margin) * 0.5;
                    frames.add(lookAt(durationSeconds * t, x, top + 20, z, cx, cy, cz));
                }
            }
            case TOP_DOWN -> {
                double height = top + span * 0.8 + 20;
                for (int i = 0; i <= LINE_STEPS; i++) {
                    double t = (double) i / LINE_STEPS;
                    frames.add(new CameraKeyframe(durationSeconds * t, cx, height, cz, (float) (90 * t), 90f));
                }
            }
            case REVEAL -> {
                for (int i = 0; i <= LINE_STEPS; i++) {
                    double t = (double) i / LINE_STEPS;
                    double ease = t * t * (3 - 2 * t);
                    double distance = radius * (0.5 + 0.7 * ease);
                    double height = target.minY() + 2 + (top - target.minY() + 20) * ease;
                    frames.add(lookAt(durationSeconds * t, cx, height, cz - distance, cx, cy, cz));
                }
            }
        }
        return new CameraPath(type, frames);
    }

    static CameraKeyframe lookAt(double time, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new CameraKeyframe(time, x, y, z, yaw, pitch);
    }
}
