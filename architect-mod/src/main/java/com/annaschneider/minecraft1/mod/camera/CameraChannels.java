package com.annaschneider.minecraft1.mod.camera;

import net.minecraft.util.Identifier;

/**
 * Networking channels of the cinematic camera. Both are server-to-client: the server stays authoritative for build and
 * job state, the client only renders the camera. Payloads are a single JSON string (see {@link CameraPacket}) and are
 * sent coalesced - never one packet per placed block.
 */
public final class CameraChannels {
    /** Tells the client which camera mode the player selected. */
    public static final Identifier CAMERA_MODE_CHANNEL = new Identifier("architect", "camera_mode");
    /** Carries the geometry and state of the job the camera should follow. */
    public static final Identifier CAMERA_STATE_CHANNEL = new Identifier("architect", "camera_state");

    private CameraChannels() {
    }
}
