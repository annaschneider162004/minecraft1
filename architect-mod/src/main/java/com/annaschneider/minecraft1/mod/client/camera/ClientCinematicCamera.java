package com.annaschneider.minecraft1.mod.client.camera;

import com.annaschneider.minecraft1.largebuild.camera.CameraKeyframe;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.camera.CameraSettings;
import com.annaschneider.minecraft1.largebuild.camera.CinematicCameraController;
import com.annaschneider.minecraft1.mod.camera.CameraPacket;
import com.annaschneider.minecraft1.mod.runtime.ArchitectConfig;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.link.LinkCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Client half of the cinematic camera. It feeds the platform-neutral {@link CinematicCameraController} with the
 * server's camera packets and applies the resulting pose to a client-only, never-spawned armour stand used as the
 * render view entity.
 * <p>
 * The real player is never moved: only the view entity changes. Perspective, HUD visibility and the view entity are
 * restored whenever filming stops, the job ends, the world changes or the client disconnects.
 */
@Environment(EnvType.CLIENT)
public final class ClientCinematicCamera {
    private static final Logger LOGGER = Logger.getLogger("com.annaschneider.minecraft1.mod.client.camera");
    /** Upper bound on the frame time fed to the controller, so a lag spike cannot jump the camera. */
    private static final double MAX_DELTA_SECONDS = 0.25;

    private final CinematicCameraController controller;
    private ArmorStandEntity view;
    private Perspective savedPerspective;
    private boolean savedHudHidden;
    private boolean active;
    private long lastNanos;

    public ClientCinematicCamera() {
        this(new CinematicCameraController(ArchitectConfig.cameraSettings()));
    }

    public ClientCinematicCamera(CinematicCameraController controller) {
        this.controller = controller;
    }

    public CinematicCameraController controller() {
        return controller;
    }

    public boolean isActive() {
        return active;
    }

    public String describe() {
        return controller.describe();
    }

    public void onSettings(MinecraftClient client, String json) {
        CameraNpcSettings raw = LinkCodec.decode(json, CameraNpcSettings.class);
        CameraNpcSettings value = new CameraNpcSettings(raw.cameraEnabled(), raw.npcEnabled(), raw.maxNpcs(),
            raw.cameraHeight(), raw.rotationSpeed());
        CameraSettings old = controller.settings();
        controller.setSettings(new CameraSettings(old.orbitDistance(), value.cameraHeight(), value.rotationSpeed(),
            old.autoShotSeconds(), old.maxMoveSpeed(), old.maxDistance()));
        if (!value.cameraEnabled()) {
            stop(client);
        }
    }

    /** Server told this client which mode to use. */
    public void onMode(MinecraftClient client, String json) {
        CameraPacket packet = CameraPacket.decode(json);
        CameraMode mode = packet.cameraMode();
        if (mode == null) {
            return;
        }
        controller.requestMode(mode);
        if (!mode.isActive()) {
            restore(client);
        }
    }

    /** Server sent a job snapshot; stale updates (an older job) are dropped by the controller. */
    public void onState(MinecraftClient client, String json) {
        CameraPacket packet = CameraPacket.decode(json);
        if (!controller.accept(packet.toUpdate())) {
            return;
        }
        if (!controller.isFilming() && active) {
            restore(client);
        }
    }

    /** Stops filming and restores the player's view. */
    public void stop(MinecraftClient client) {
        controller.stop();
        restore(client);
    }

    /** The player left the world: forget the job but keep the chosen mode for the next one. */
    public void onDisconnect(MinecraftClient client) {
        controller.clearJob();
        restore(client);
    }

    /** Call once per client tick. */
    public void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }
        if (client.world == null || client.player == null) {
            if (active) {
                onDisconnect(client);
            }
            lastNanos = 0;
            return;
        }
        controller.setClearance((x, y, z) -> {
            try {
                return client.world.isAir(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
            } catch (RuntimeException ex) {
                return true;
            }
        });
        double delta = delta();
        Optional<CameraKeyframe> pose = controller.tick(delta);
        if (pose.isEmpty()) {
            if (active) {
                restore(client);
            }
            return;
        }
        try {
            apply(client, pose.get());
        } catch (RuntimeException ex) {
            LOGGER.warning("[Architect] Cinematic camera failed, restoring the normal view: " + ex);
            stop(client);
        }
    }

    private double delta() {
        long now = System.nanoTime();
        if (lastNanos == 0) {
            lastNanos = now;
            return 0;
        }
        double seconds = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        return Math.min(MAX_DELTA_SECONDS, Math.max(0, seconds));
    }

    private void apply(MinecraftClient client, CameraKeyframe frame) {
        if (view == null || view.isRemoved()) {
            view = new ArmorStandEntity(client.world, frame.x(), frame.y(), frame.z());
            view.setInvisible(true);
            view.setNoGravity(true);
            view.setInvulnerable(true);
            view.setSilent(true);
            view.refreshPositionAndAngles(frame.x(), frame.y(), frame.z(), (float) frame.yaw(), (float) frame.pitch());
        }
        // keep the previous pose so the renderer interpolates instead of snapping
        view.prevX = view.getX();
        view.prevY = view.getY();
        view.prevZ = view.getZ();
        view.prevYaw = view.getYaw();
        view.prevPitch = view.getPitch();
        view.setPos(frame.x(), frame.y(), frame.z());
        view.setYaw((float) frame.yaw());
        view.setPitch((float) frame.pitch());
        view.setHeadYaw((float) frame.yaw());
        if (!active) {
            savedPerspective = client.options.getPerspective();
            savedHudHidden = client.options.hudHidden;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.options.hudHidden = true;
            active = true;
        }
        client.setCameraEntity(view);
    }

    /** Puts the player back behind their own eyes; safe to call when nothing was changed. */
    private void restore(MinecraftClient client) {
        if (client != null && active) {
            try {
                client.setCameraEntity(client.player);
                if (savedPerspective != null) {
                    client.options.setPerspective(savedPerspective);
                }
                client.options.hudHidden = savedHudHidden;
            } catch (RuntimeException ex) {
                LOGGER.warning("[Architect] Could not restore the player view: " + ex);
            }
        }
        active = false;
        savedPerspective = null;
        savedHudHidden = false;
        lastNanos = 0;
        if (view != null) {
            view.discard();
            view = null;
        }
    }
}
