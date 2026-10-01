package com.annaschneider.minecraft1.mod.camera;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.camera.CameraUpdate;
import com.annaschneider.minecraft1.largebuild.camera.CinematicCameraController;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server-to-client camera wire format and how the client state machine consumes it. */
class CameraPacketTest {
    private static CameraPacket roundTrip(CameraPacket packet) {
        return CameraPacket.decode(packet.encode());
    }

    @Test
    void modePacketCarriesOnlyTheMode() {
        CameraPacket packet = roundTrip(CameraPacket.ofMode(CameraMode.AUTO));
        assertEquals(CameraMode.AUTO, packet.cameraMode());
        assertEquals(0, packet.jobId());
        assertNull(packet.target());
        assertNull(CameraPacket.decode(CameraPacket.ofUpdate(update(7, JobState.RUNNING), "minecraft:overworld").encode()).cameraMode());
    }

    @Test
    void statePacketKeepsJobGeometryAndProgress() {
        CameraPacket packet = roundTrip(CameraPacket.ofUpdate(update(42, JobState.RUNNING), "minecraft:the_nether"));
        CameraUpdate decoded = packet.toUpdate();
        assertEquals(42, decoded.jobId());
        assertEquals("minecraft:the_nether", decoded.worldId());
        assertEquals(JobState.RUNNING, decoded.state());
        assertEquals(new Bounds(0, 0, 0, 31, 15, 31), decoded.target());
        assertEquals(new Bounds(0, 0, 0, 15, 7, 15), decoded.built());
        assertEquals(new Bounds(8, 0, 8, 15, 7, 15), decoded.frontier());
        assertEquals(25.0, decoded.percent(), 1e-9);
    }

    @Test
    void unknownStateFallsBackToRunningAndBadBoundsAreDropped() {
        CameraPacket broken = new CameraPacket(null, 3, "w", "NOT_A_STATE", new int[] {1, 2}, null, null, 0, 10);
        CameraUpdate update = roundTrip(broken).toUpdate();
        assertEquals(JobState.RUNNING, update.state());
        assertNull(update.target());
    }

    @Test
    void clientIgnoresUpdatesOfAnOlderJob() {
        CinematicCameraController controller = new CinematicCameraController();
        controller.requestMode(CameraMode.ORBIT);
        assertTrue(controller.accept(roundTrip(CameraPacket.ofUpdate(update(10, JobState.RUNNING), "w")).toUpdate()));
        assertFalse(controller.accept(roundTrip(CameraPacket.ofUpdate(update(9, JobState.RUNNING), "w")).toUpdate()));
        assertEquals(10, controller.jobId());
    }

    @Test
    void terminalStatePacketStopsFilming() {
        CinematicCameraController controller = new CinematicCameraController();
        controller.requestMode(CameraMode.AUTO);
        controller.accept(roundTrip(CameraPacket.ofUpdate(update(10, JobState.RUNNING), "w")).toUpdate());
        assertTrue(controller.isFilming());
        controller.accept(roundTrip(CameraPacket.ofUpdate(update(10, JobState.CANCELLED), "w")).toUpdate());
        assertFalse(controller.isFilming());
        assertTrue(controller.pose().isEmpty());
    }

    private static CameraUpdate update(long jobId, JobState state) {
        return new CameraUpdate(jobId, "minecraft:overworld", state, new Bounds(0, 0, 0, 31, 15, 31),
            new Bounds(0, 0, 0, 15, 7, 15), new Bounds(8, 0, 8, 15, 7, 15), 250, 1000);
    }
}
