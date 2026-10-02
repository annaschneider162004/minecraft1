package com.annaschneider.minecraft1.mod.client;

import com.annaschneider.minecraft1.largebuild.camera.CameraSettings;
import com.annaschneider.minecraft1.mod.client.camera.ClientCinematicCamera;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.link.LinkCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientCinematicCameraSettingsTest {
    @Test
    void cameraSettingsResetWhenDisconnectingFromWorld() {
        ClientCinematicCamera camera = new ClientCinematicCamera();
        CameraSettings defaults = camera.controller().settings();
        camera.onSettings(null, LinkCodec.encode(new CameraNpcSettings(true, false, 2, 40, 20)));
        assertEquals(40, camera.controller().settings().orbitHeight());
        assertEquals(20, camera.controller().settings().orbitSpeedDegreesPerSecond());
        camera.onDisconnect(null);
        assertEquals(defaults, camera.controller().settings());
    }
}
