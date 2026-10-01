package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.camera.CameraKeyframe;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.camera.CameraSettings;
import com.annaschneider.minecraft1.largebuild.camera.CameraUpdate;
import com.annaschneider.minecraft1.largebuild.camera.CinematicCameraController;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lifecycle, stale-update and smoothing behaviour of the live cinematic camera state machine. */
class CinematicCameraControllerTest {
    private static final Bounds TARGET = new Bounds(0, 64, 0, 63, 95, 63);
    private static final Bounds SECTION = new Bounds(16, 64, 16, 31, 79, 31);

    private static CameraUpdate update(long jobId, JobState state, Bounds target, Bounds built, Bounds frontier) {
        return new CameraUpdate(jobId, "minecraft:overworld", state, target, built, frontier, 1, 10);
    }

    @Test
    void startsFilmingOnlyWithAJobAndRestoresOnTerminalStates() {
        for (JobState terminal : new JobState[] {JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED}) {
            CinematicCameraController camera = new CinematicCameraController();
            camera.requestMode(CameraMode.AUTO);
            assertFalse(camera.isFilming(), "no build yet");
            assertTrue(camera.tick(0.05).isEmpty());

            assertTrue(camera.accept(update(7, JobState.RUNNING, TARGET, null, SECTION)));
            assertTrue(camera.isFilming());
            assertTrue(camera.tick(0.05).isPresent());

            assertTrue(camera.accept(update(7, terminal, TARGET, TARGET, null)));
            assertFalse(camera.isFilming(), terminal + " must stop the camera");
            assertTrue(camera.tick(0.05).isEmpty());
            assertTrue(camera.pose().isEmpty(), "the pose is dropped so the client restores the player view");
        }
    }

    @Test
    void ignoresUpdatesOfOlderJobs() {
        CinematicCameraController camera = new CinematicCameraController();
        camera.requestMode(CameraMode.ORBIT);
        assertTrue(camera.accept(update(12, JobState.RUNNING, TARGET, null, SECTION)));
        assertEquals(12, camera.jobId());

        assertFalse(camera.accept(update(11, JobState.COMPLETED, TARGET, TARGET, null)), "stale job");
        assertEquals(12, camera.jobId());
        assertTrue(camera.isFilming(), "a stale terminal update must not stop the current shot");

        assertTrue(camera.accept(update(13, JobState.RUNNING, new Bounds(200, 64, 200, 215, 79, 215), null, null)));
        assertEquals(13, camera.jobId());
    }

    @Test
    void stopAndDisconnectClearEverything() {
        CinematicCameraController camera = new CinematicCameraController();
        camera.requestMode(CameraMode.FOLLOW);
        camera.accept(update(1, JobState.RUNNING, TARGET, null, SECTION));
        camera.tick(0.05);
        camera.stop();
        assertSame(CameraMode.OFF, camera.mode());
        assertFalse(camera.isFilming());
        assertTrue(camera.pose().isEmpty());

        camera.requestMode(CameraMode.WIDE);
        camera.accept(update(2, JobState.RUNNING, TARGET, null, SECTION));
        camera.tick(0.05);
        camera.clearJob();
        assertFalse(camera.isFilming(), "leaving the world stops filming");
        assertEquals(0, camera.jobId());
        assertTrue(camera.tick(0.05).isEmpty());
    }

    @Test
    void movesSmoothlyOutsideTheBuildAndHoldsStillWhilePaused() {
        CameraSettings settings = CameraSettings.defaults();
        CinematicCameraController camera = new CinematicCameraController(settings);
        camera.requestMode(CameraMode.ORBIT);
        camera.accept(update(1, JobState.RUNNING, TARGET, TARGET, SECTION));
        CameraKeyframe previous = camera.tick(0.05).orElseThrow();
        for (int i = 0; i < 100; i++) {
            CameraKeyframe next = camera.tick(0.05).orElseThrow();
            double step = Math.sqrt(Math.pow(next.x() - previous.x(), 2) + Math.pow(next.y() - previous.y(), 2)
                + Math.pow(next.z() - previous.z(), 2));
            assertTrue(step <= settings.maxMoveSpeed() * 0.05 + 1e-6, "camera teleported " + step + " blocks");
            assertFalse(TARGET.contains((int) Math.floor(next.x()), (int) Math.floor(next.y()), (int) Math.floor(next.z())),
                "camera must stay outside the build");
            previous = next;
        }

        camera.accept(update(1, JobState.PAUSED, TARGET, TARGET, SECTION));
        assertTrue(camera.isPaused());
        CameraKeyframe held = camera.tick(0.05).orElseThrow();
        assertEquals(held, camera.tick(0.05).orElseThrow(), "a paused job freezes the camera");

        camera.accept(update(1, JobState.RUNNING, TARGET, TARGET, SECTION));
        assertFalse(camera.isPaused());
        assertTrue(camera.tick(0.05).isPresent());
    }

    @Test
    void automaticModeCyclesShotsAtBoundedIntervals() {
        CinematicCameraController camera = new CinematicCameraController(new CameraSettings(18, 12, 9, 3, 18, 192));
        camera.requestMode(CameraMode.AUTO);
        camera.accept(update(1, JobState.RUNNING, TARGET, TARGET, SECTION));
        assertSame(CameraMode.ORBIT, camera.shot());
        for (int i = 0; i < 61; i++) {
            camera.tick(0.05);
        }
        assertSame(CameraMode.FOLLOW, camera.shot());
        for (int i = 0; i < 61; i++) {
            camera.tick(0.05);
        }
        assertSame(CameraMode.WIDE, camera.shot());
    }

    @Test
    void raisesTheCameraOutOfTerrain() {
        CinematicCameraController camera = new CinematicCameraController();
        camera.setClearance((x, y, z) -> y >= 150);
        camera.requestMode(CameraMode.ORBIT);
        camera.accept(update(1, JobState.RUNNING, TARGET, TARGET, SECTION));
        assertTrue(camera.tick(0.05).orElseThrow().y() >= 150, "camera must climb above solid ground");
    }

    @Test
    void parsesModeIdsAndRejectsUnknownOnes() {
        assertSame(CameraMode.OFF, CameraMode.fromId("stop"));
        assertSame(CameraMode.AUTO, CameraMode.fromId("auto"));
        assertSame(CameraMode.WIDE, CameraMode.fromId("WIDE"));
        assertThrows(IllegalArgumentException.class, () -> CameraMode.fromId("spin"));

        CameraSettings clamped = new CameraSettings(-5, 1_000, 0, 0, 0, 10_000);
        assertEquals(3.0, clamped.orbitDistance());
        assertEquals(96.0, clamped.orbitHeight());
        assertEquals(512.0, clamped.maxDistance());
    }
}
