package com.annaschneider.minecraft1.mod.command;

import com.annaschneider.minecraft1.mod.runtime.InMemoryBlockWorld;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectCommandEngineTest {
    @Test
    void executesBuildAndUndoFlow() {
        ArchitectCommandEngine engine = new ArchitectCommandEngine();
        InMemoryBlockWorld world = new InMemoryBlockWorld();
        UUID player = UUID.randomUUID();

        CommandResult build = engine.execute(player, world, "/architect build house");
        assertTrue(build.success());

        while (engine.tick(world).completedSessions() == 0) {
            // process deterministic queue
        }

        CommandResult undo = engine.execute(player, world, "/architect undo");
        assertTrue(undo.success());
    }

    @Test
    void rejectsUnknownTemplate() {
        ArchitectCommandEngine engine = new ArchitectCommandEngine();
        InMemoryBlockWorld world = new InMemoryBlockWorld();

        CommandResult result = engine.execute(UUID.randomUUID(), world, "/architect build unknown");

        assertFalse(result.success());
    }
}
