package com.annaschneider.minecraft1.mod.command;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.mod.runtime.InMemoryBlockWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectLargeBuildCommandsTest {
    @TempDir
    Path dataRoot;
    private ArchitectCommandEngine engine;
    private InMemoryBlockWorld world;
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BuildSettings settings = new BuildSettings(16_384, 256, 0, 16, 2, 4_000_000L, 262_144, dataRoot.resolve("journals"));
        engine = new ArchitectCommandEngine(dataRoot, settings);
        world = new InMemoryBlockWorld();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private CommandResult run(String command) {
        return engine.execute(player, world, new Vec3i(5, 64, -7), command);
    }

    private void tickUntilIdle() {
        for (int i = 0; i < 10_000; i++) {
            if (engine.tick(world).remainingJobs() == 0) {
                return;
            }
        }
        throw new AssertionError("queue did not drain");
    }

    @Test
    void imagePlanPreviewBuildCancelAndUndo() {
        CommandResult plan = run("/architect image plan placeholder:dream dream 1");
        assertTrue(plan.success(), plan.message());
        assertTrue(Files.exists(dataRoot.resolve("plans/dream.json")));

        CommandResult preview = run("/architect image preview");
        assertTrue(preview.success(), preview.message());
        assertTrue(preview.message().contains("palace=1"), preview.message());

        assertTrue(run("/architect image load dream").success());
        CommandResult build = run("/architect image build");
        assertTrue(build.success(), build.message());
        CommandResult conflict = run("/architect mega palace");
        assertFalse(conflict.success());
        assertTrue(conflict.message().contains("already in progress"), conflict.message());

        engine.tick(world);
        engine.tick(world);
        assertTrue(run("/architect progress").message().contains("running"));
        assertTrue(run("/architect queue").message().contains("scene-dream"));
        assertTrue(run("/architect cancel").success());
        assertFalse(run("/architect cancel").success());

        assertTrue(run("/architect undo").success());
        tickUntilIdle();
        assertEquals("minecraft:air", world.getBlock(new Vec3i(0, 64, -16)));
        assertFalse(run("/architect undo").success());
    }

    @Test
    void reportsClearErrors() {
        assertTrue(run("/architect image load nothing").message().contains("No saved plan 'nothing'"));
        assertTrue(run("/architect image preview").message().contains("No plan loaded"));
        assertTrue(run("/architect image plan uploads/../../etc/passwd.png evil").message().contains("Invalid image path"));
        assertTrue(run("/architect image plan /etc/passwd.png evil").message().contains("Invalid image path"));
        assertTrue(run("/architect image plan uploads/missing.png p").message().contains("Image not found"));
        assertTrue(run("/architect image plan placeholder:x BAD!").message().contains("Invalid plan id"));
        assertTrue(run("/architect image plan placeholder:x p 999").message().contains("scale"));
        assertTrue(run("/architect blueprint build ghost").message().contains("No saved blueprint 'ghost'"));
        assertTrue(run("/architect mega dragon").message().contains("Unknown mega structure"));
        assertTrue(run("/architect mega bridge 40 45").message().contains("rotation"));
        assertTrue(run("/architect mega bridge 40 90 y").message().contains("mirror"));
        assertTrue(run("/architect mega island 400").message().contains("Move the origin"), run("/architect mega island 400").message());
        assertTrue(run("/architect camera orbit").message().contains("No build to film"));
        assertFalse(run("/architect progress").success());
        assertTrue(run("/architect help").message().contains("/architect image plan"));
    }

    @Test
    void megaStructureBuildsWithRotationAndMirrorAndUndoes() {
        CommandResult mega = run("/architect mega bridge 48 90 z");
        assertTrue(mega.success(), mega.message());
        tickUntilIdle();
        assertTrue(run("/architect progress").message().contains("completed"));
        // origin snaps to the chunk grid (0, 64, -16); rotation 90 runs the bridge along +Z
        assertEquals("minecraft:smooth_quartz", world.getBlock(new Vec3i(0, 64, -16 + 30)));
        assertEquals("minecraft:air", world.getBlock(new Vec3i(30, 64, -16)));
        assertTrue(run("/architect camera orbit 10").success());
        assertTrue(run("/architect undo").success());
        tickUntilIdle();
        assertEquals("minecraft:air", world.getBlock(new Vec3i(0, 64, -16 + 30)));
    }

    @Test
    void exportsPlanAndBuildsSavedBlueprint() {
        assertTrue(run("/architect image plan placeholder:cherry-garden grove 1").success());
        CommandResult export = run("/architect image export grove");
        assertTrue(export.success(), export.message());
        assertTrue(engine.awaitExports(60, TimeUnit.SECONDS));
        assertTrue(run("/architect blueprint list").message().contains("grove"), run("/architect blueprint list").message());
        assertTrue(Files.exists(dataRoot.resolve("blueprints/grove.mcab")));

        CommandResult build = run("/architect blueprint build grove");
        assertTrue(build.success(), build.message());
        tickUntilIdle();
        assertTrue(run("/architect progress").message().contains("completed"));
        assertTrue(run("/architect image list").message().contains("grove"));
    }
}
