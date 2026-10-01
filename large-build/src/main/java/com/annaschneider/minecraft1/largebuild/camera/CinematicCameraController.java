package com.annaschneider.minecraft1.largebuild.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.engine.JobState;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Platform-neutral state machine of the live cinematic camera. It owns the camera mode, the job it currently films and
 * the smoothed camera pose; the platform layer (the Fabric client) only feeds it {@link CameraUpdate}s plus a delta
 * time and applies {@link #pose()} to an actual camera.
 * <p>
 * The controller never moves a player: it just produces poses. It stops itself when the filmed job reaches a terminal
 * state, when the dimension changes or when {@link #stop()} is called, which is what the client uses as the signal to
 * restore the original view.
 */
public final class CinematicCameraController {
    /** Line-of-sight test used to keep the camera out of terrain. */
    @FunctionalInterface
    public interface Clearance {
        boolean isClear(double x, double y, double z);
    }

    private static final double MIN_RADIUS = 6;
    private static final double MAX_LIFT = 48;
    private static final double LIFT_STEP = 2;

    private final CameraSettings settings;
    private Clearance clearance = (x, y, z) -> true;

    private CameraMode mode = CameraMode.OFF;
    private CameraMode shot = CameraMode.ORBIT;
    private boolean filming;
    private boolean paused;

    private long jobId;
    private String worldId;
    private JobState state;
    private Bounds target;
    private Bounds built;
    private Bounds frontier;
    private double percent;

    private double shotElapsed;
    private double orbitAngle;
    private CameraKeyframe pose;

    public CinematicCameraController() {
        this(CameraSettings.defaults());
    }

    public CinematicCameraController(CameraSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public CameraSettings settings() {
        return settings;
    }

    public void setClearance(Clearance clearance) {
        this.clearance = clearance == null ? (x, y, z) -> true : clearance;
    }

    public CameraMode mode() {
        return mode;
    }

    /** Shot currently rendered; equals {@link #mode()} unless the mode is {@link CameraMode#AUTO}. */
    public CameraMode shot() {
        return shot;
    }

    public boolean isFilming() {
        return filming;
    }

    public boolean isPaused() {
        return paused;
    }

    public long jobId() {
        return jobId;
    }

    public Optional<String> worldId() {
        return Optional.ofNullable(worldId);
    }

    public Optional<CameraKeyframe> pose() {
        return Optional.ofNullable(pose);
    }

    /**
     * Switches the camera mode. {@link CameraMode#OFF} stops filming (and asks the platform to restore the view);
     * any other mode starts filming as soon as a build area is known.
     */
    public void requestMode(CameraMode requested) {
        CameraMode next = requested == null ? CameraMode.OFF : requested;
        if (next == CameraMode.OFF) {
            stop();
            return;
        }
        if (mode != next) {
            shotElapsed = 0;
        }
        mode = next;
        shot = next == CameraMode.AUTO ? CameraMode.ORBIT : next;
        filming = hasArea();
        if (!filming) {
            pose = null;
        }
    }

    /** Stops filming and forgets the pose; the mode becomes {@link CameraMode#OFF}. */
    public void stop() {
        mode = CameraMode.OFF;
        filming = false;
        paused = false;
        pose = null;
        shotElapsed = 0;
    }

    /** Forgets the filmed job (used when the player leaves the world) without changing the requested mode. */
    public void clearJob() {
        jobId = 0;
        worldId = null;
        state = null;
        target = null;
        built = null;
        frontier = null;
        percent = 0;
        filming = false;
        paused = false;
        pose = null;
    }

    /**
     * Applies a server update.
     *
     * @return {@code false} when the update belongs to an older job (stale) and was ignored
     */
    public boolean accept(CameraUpdate update) {
        if (update == null || update.jobId() < jobId) {
            return false;
        }
        if (update.jobId() > jobId) {
            jobId = update.jobId();
            target = null;
            built = null;
            frontier = null;
            pose = null;
            shotElapsed = 0;
        }
        if (worldId != null && update.worldId() != null && !worldId.equals(update.worldId())) {
            // the job moved to another dimension (or we follow a job of a different world): restart framing
            pose = null;
        }
        worldId = update.worldId();
        state = update.state();
        percent = update.percent();
        if (update.target() != null) {
            target = target == null ? update.target() : target.union(update.target());
        }
        if (update.built() != null) {
            built = built == null ? update.built() : built.union(update.built());
        }
        if (update.frontier() != null) {
            frontier = update.frontier();
        }
        paused = update.state() == JobState.PAUSED;
        if (update.isFinished()) {
            filming = false;
            paused = false;
            pose = null;
        } else if (mode.isActive()) {
            filming = hasArea();
        }
        return true;
    }

    /**
     * Advances the camera by {@code deltaSeconds} and returns the new pose, or an empty optional when nothing is
     * filmed. While the job is paused the camera holds its last pose instead of progressing.
     */
    public Optional<CameraKeyframe> tick(double deltaSeconds) {
        if (!filming || !hasArea()) {
            return Optional.empty();
        }
        double dt = Double.isNaN(deltaSeconds) ? 0 : Math.max(0, Math.min(1, deltaSeconds));
        if (paused) {
            return Optional.ofNullable(pose);
        }
        shotElapsed += dt;
        if (mode == CameraMode.AUTO && shotElapsed >= settings.autoShotSeconds()) {
            shotElapsed = 0;
            shot = nextShot(shot);
        }
        orbitAngle = (orbitAngle + settings.orbitSpeedDegreesPerSecond() * dt) % 360;
        pose = smooth(desiredPose(), dt);
        return Optional.of(pose);
    }

    /** Short, user-facing description for command feedback and the desktop app. */
    public String describe() {
        if (!mode.isActive()) {
            return "Cinematic camera off.";
        }
        if (!filming) {
            return String.format(Locale.ROOT, "Cinematic camera armed (%s); waiting for a build.", mode.id());
        }
        String suffix = paused ? " (paused)" : "";
        return String.format(Locale.ROOT, "Cinematic camera %s on job #%d, %.0f%% built%s.", shot.id(), jobId, percent, suffix);
    }

    private static CameraMode nextShot(CameraMode current) {
        return switch (current) {
            case ORBIT -> CameraMode.FOLLOW;
            case FOLLOW -> CameraMode.WIDE;
            default -> CameraMode.ORBIT;
        };
    }

    private boolean hasArea() {
        return target != null || built != null || frontier != null;
    }

    private Bounds framed() {
        return switch (shot) {
            case FOLLOW -> frontier != null ? frontier : fallbackArea();
            case WIDE -> target != null ? target : fallbackArea();
            default -> built != null ? built : fallbackArea();
        };
    }

    private Bounds fallbackArea() {
        if (built != null) {
            return built;
        }
        if (target != null) {
            return target;
        }
        return frontier;
    }

    private CameraKeyframe desiredPose() {
        Bounds area = framed();
        double cx = (area.minX() + area.maxX() + 1) / 2.0;
        double cy = (area.minY() + area.maxY() + 1) / 2.0;
        double cz = (area.minZ() + area.maxZ() + 1) / 2.0;
        double span = Math.max(area.sizeX(), area.sizeZ());
        double factor = switch (shot) {
            case FOLLOW -> 0.6;
            case WIDE -> 1.2;
            default -> 0.75;
        };
        double radius = clamp(span * factor + settings.orbitDistance(), MIN_RADIUS, settings.maxDistance());
        double height = cy + area.sizeY() * 0.4 + settings.orbitHeight() * (shot == CameraMode.WIDE ? 1.8 : 1.0);
        double angle = Math.toRadians(shot == CameraMode.WIDE ? 225 : orbitAngle);
        double x = cx + Math.cos(angle) * radius;
        double z = cz + Math.sin(angle) * radius;
        double y = lift(x, height, z);
        return DeterministicShotPlanner.lookAt(0, x, y, z, cx, cy, cz);
    }

    /** Raises the camera in bounded steps until it is no longer inside terrain. */
    private double lift(double x, double y, double z) {
        double lifted = y;
        for (double used = 0; used <= MAX_LIFT; used += LIFT_STEP) {
            if (clearance.isClear(x, lifted, z)) {
                return lifted;
            }
            lifted += LIFT_STEP;
        }
        return lifted;
    }

    /** Clamps how far the camera may travel in one tick so it glides instead of teleporting. */
    private CameraKeyframe smooth(CameraKeyframe desired, double dt) {
        if (pose == null) {
            return desired;
        }
        double dx = desired.x() - pose.x();
        double dy = desired.y() - pose.y();
        double dz = desired.z() - pose.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double allowed = settings.maxMoveSpeed() * dt;
        if (distance <= allowed || distance <= 1e-9) {
            return desired;
        }
        double t = allowed / distance;
        double x = pose.x() + dx * t;
        double y = pose.y() + dy * t;
        double z = pose.z() + dz * t;
        Bounds area = framed();
        double cx = (area.minX() + area.maxX() + 1) / 2.0;
        double cy = (area.minY() + area.maxY() + 1) / 2.0;
        double cz = (area.minZ() + area.maxZ() + 1) / 2.0;
        return DeterministicShotPlanner.lookAt(0, x, y, z, cx, cy, cz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
