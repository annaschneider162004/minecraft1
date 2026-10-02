package com.annaschneider.minecraft1.mod.command;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.mod.runtime.InMemoryBlockWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /architect camera ...} and {@code /architect npc ...} against fake platform controls. */
class CameraNpcCommandsTest {
    @TempDir
    Path dataRoot;
    private ArchitectCommandEngine engine;
    private InMemoryBlockWorld world;
    private final Map<UUID, CameraMode> modes = new HashMap<>();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private boolean npcEnabled = true;
    private CameraNpcSettings receivedCamera;
    private CameraNpcSettings receivedCrew;

    @BeforeEach
    void setUp() {
        BuildSettings settings = new BuildSettings(16_384, 256, 0, 16, 2, 4_000_000L, 262_144, dataRoot.resolve("journals"));
        engine = new ArchitectCommandEngine(dataRoot, settings);
        world = new InMemoryBlockWorld();
        engine.setCameraControl(new ArchitectCommandEngine.CameraControl() {
            @Override
            public String setMode(UUID playerId, CameraMode mode) {
                modes.put(playerId, mode);
                return "Cinematic camera: " + mode.id() + ".";
            }

            @Override
            public String describe(UUID playerId) {
                return "Cinematic camera: " + modes.getOrDefault(playerId, CameraMode.OFF).id() + ".";
            }

            @Override
            public void applySettings(UUID playerId, CameraNpcSettings settings) {
                receivedCamera = settings;
            }
        });
        engine.setCrewControl(new ArchitectCommandEngine.CrewControl() {
            @Override
            public String setEnabled(boolean enabled) {
                npcEnabled = enabled;
                return enabled ? "Builder NPCs enabled." : "Builder NPCs disabled.";
            }

            @Override
            public String describe() {
                return "Builder NPCs " + (npcEnabled ? "enabled" : "disabled") + ".";
            }

            @Override
            public void applySettings(CameraNpcSettings settings) {
                receivedCrew = settings;
            }
        });
    }

    @Test
    void appliesBoundedSettingsAndIsSafeWithoutPlayer() {
        CameraNpcSettings incoming = new CameraNpcSettings(true, true, 100, 100, -1);
        assertTrue(engine.applyCameraNpcSettings(null, incoming).contains("No player"));
        assertEquals(null, receivedCamera);
        assertTrue(engine.applyCameraNpcSettings(alice, incoming).contains("applied"));
        assertEquals(new CameraNpcSettings(true, true, 12, 80, 1), receivedCamera);
        assertEquals(receivedCamera, receivedCrew);
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private CommandResult run(UUID player, String command) {
        return engine.execute(player, world, new Vec3i(0, 64, 0), command);
    }

    @Test
    void liveModesAreForwardedToTheCameraControl() {
        for (String mode : new String[] {"auto", "orbit", "follow", "wide"}) {
            CommandResult result = run(alice, "/architect camera " + mode);
            assertTrue(result.success(), result.message());
            assertEquals(CameraMode.fromId(mode), modes.get(alice));
        }
        assertTrue(run(alice, "/architect camera stop").success());
        assertEquals(CameraMode.OFF, modes.get(alice));
        assertTrue(run(alice, "/architect camera status").message().contains("off"));
    }

    @Test
    void cameraCommandsOnlyAffectTheRequestingPlayer() {
        run(alice, "/architect camera follow");
        run(bob, "/architect camera wide");
        assertEquals(CameraMode.FOLLOW, modes.get(alice));
        assertEquals(CameraMode.WIDE, modes.get(bob));
        run(alice, "/architect camera stop");
        assertEquals(CameraMode.OFF, modes.get(alice));
        assertEquals(CameraMode.WIDE, modes.get(bob));
    }

    @Test
    void offlineShotPlanningStillHandlesTheLegacyShotTypes() {
        assertTrue(run(alice, "/architect camera flyby").message().contains("No build to film"));
        assertTrue(run(alice, "/architect build house").success());
        for (int i = 0; i < 200 && engine.queue().isBusy(alice); i++) {
            engine.tick(world);
        }
        assertTrue(run(alice, "/architect camera flyby 10").success());
    }

    @Test
    void npcCommandsToggleAndReportTheCrew() {
        assertTrue(run(alice, "/architect npc off").success());
        assertFalse(npcEnabled);
        assertTrue(run(alice, "/architect npc status").message().contains("disabled"));
        assertTrue(run(alice, "/architect npc on").success());
        assertTrue(npcEnabled);
        assertFalse(run(alice, "/architect npc sideways").success());
    }

    @Test
    void helpListsTheNewControls() {
        String help = run(alice, "/architect help").message();
        assertTrue(help.contains("/architect camera <auto|orbit|follow|wide|stop|status>"), help);
        assertTrue(help.contains("/architect npc <on|off|status>"), help);
    }
}
