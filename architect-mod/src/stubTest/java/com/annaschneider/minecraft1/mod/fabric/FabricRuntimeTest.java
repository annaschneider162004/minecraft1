package com.annaschneider.minecraft1.mod.fabric;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.engine.JobKind;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.engine.JobState;
import com.annaschneider.minecraft1.link.RecordingStatus;
import com.annaschneider.minecraft1.mod.link.PlayerContext;
import com.annaschneider.minecraft1.mod.recording.ServerRecordingCoordinator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class FabricRuntimeTest {
    private MinecraftServer server;
    private ServerWorld world;
    private ServerPlayerEntity player;

    @BeforeEach
    void setup() {
        server = new MinecraftServer();
        world = server.getOverworld();
        player = new ServerPlayerEntity(server, world, UUID.randomUUID(), "Alice");
        server.getPlayerManager().addPlayer(player);
    }

    @Test
    void fabricBlockWorldPlacesAndRetrievesBlocks() {
        FabricBlockWorld blockWorld = new FabricBlockWorld(world);
        Vec3i pos = new Vec3i(10, 64, 20);
        assertTrue(blockWorld.prepareChunk(pos.x() >> 4, pos.z() >> 4), "the chunk ticket loads the chunk");

        blockWorld.setBlock(pos, "minecraft:stone");
        assertEquals("minecraft:stone", blockWorld.getBlock(pos));

        // Setting air clears
        blockWorld.setBlock(pos, "minecraft:air");
        assertEquals("minecraft:air", blockWorld.getBlock(pos));
    }

    @Test
    void fabricBlockWorldEnforcesHeightAndBorderBounds() {
        FabricBlockWorld blockWorld = new FabricBlockWorld(world);

        // Outside height bounds (-64 to 320)
        Vec3i below = new Vec3i(0, -100, 0);
        Vec3i above = new Vec3i(0, 400, 0);
        assertThrows(IllegalArgumentException.class, () -> blockWorld.setBlock(below, "minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> blockWorld.setBlock(above, "minecraft:stone"));
        assertEquals("minecraft:void_air", blockWorld.getBlock(below));
        assertEquals("minecraft:void_air", blockWorld.getBlock(above));

        // Outside world border
        Vec3i outsideBorder = new Vec3i(40_000_000, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> blockWorld.setBlock(outsideBorder, "minecraft:stone"));
        assertEquals("minecraft:void_air", blockWorld.getBlock(outsideBorder));
        assertTrue(blockWorld.withinBorder(new Bounds(-10, 0, -10, 10, 10, 10)));
        assertFalse(blockWorld.withinBorder(new Bounds(0, 0, 0, 40_000_000, 10, 10)));
    }

    @Test
    void fabricBlockWorldRejectsInvalidBlockIdsWithClearErrors() {
        FabricBlockWorld blockWorld = new FabricBlockWorld(world);
        Vec3i pos = new Vec3i(5, 64, 5);

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
            () -> blockWorld.setBlock(pos, "invalid_mod:non_existent_block_xyz"));
        assertTrue(unknown.getMessage().contains("Unknown block"), unknown.getMessage());
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
            () -> blockWorld.setBlock(pos, "Not A Block!"));
        assertTrue(malformed.getMessage().contains("Invalid block identifier"), malformed.getMessage());
        assertThrows(IllegalArgumentException.class, () -> blockWorld.setBlock(pos, " "));
        assertEquals("minecraft:air", blockWorld.getBlock(pos));
    }

    @Test
    void fabricBlockWorldManagesChunkTickets() {
        FabricBlockWorld blockWorld = new FabricBlockWorld(world);
        int chunkX = 2;
        int chunkZ = 3;
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        assertFalse(world.getChunkManager().hasTicket(ChunkTicketType.FORCED, chunkPos));

        blockWorld.prepareChunk(chunkX, chunkZ);
        assertTrue(world.getChunkManager().hasTicket(ChunkTicketType.FORCED, chunkPos));

        blockWorld.releaseChunk(chunkX, chunkZ);
        assertFalse(world.getChunkManager().hasTicket(ChunkTicketType.FORCED, chunkPos));

        // Test release all
        blockWorld.prepareChunk(10, 10);
        blockWorld.prepareChunk(11, 11);
        blockWorld.releaseAllChunks();
        assertFalse(world.getChunkManager().hasTicket(ChunkTicketType.FORCED, new ChunkPos(10, 10)));
        assertFalse(world.getChunkManager().hasTicket(ChunkTicketType.FORCED, new ChunkPos(11, 11)));
    }

    @Test
    void playerDirectoryFindsSinglePlayerAutomatically() {
        FabricPlayerDirectory directory = new FabricPlayerDirectory(server);
        Optional<PlayerContext> context = directory.find(null);

        assertTrue(context.isPresent());
        assertEquals("Alice", context.get().name());
        assertEquals(player.getUuid(), context.get().id());
        assertEquals(new Vec3i(0, 64, 0), context.get().position());
    }

    @Test
    void playerDirectoryReturnsEmptyWhenNobodyIsOnlineOrNameIsUnknown() {
        FabricPlayerDirectory directory = new FabricPlayerDirectory(server);
        assertTrue(directory.find("Nobody").isEmpty());

        server.getPlayerManager().removePlayer(player);
        assertTrue(directory.find(null).isEmpty());
    }

    @Test
    void playerDirectoryUsesThePlayersCurrentWorld() {
        ServerWorld nether = new ServerWorld(server, World.NETHER);
        server.addWorld(World.NETHER, nether);
        ServerPlayerEntity traveller = new ServerPlayerEntity(server, nether, UUID.randomUUID(), "Traveller");
        server.getPlayerManager().addPlayer(traveller);

        FabricPlayerDirectory directory = new FabricPlayerDirectory(server);
        PlayerContext context = directory.find("Traveller").orElseThrow();
        assertSame(nether, ((FabricBlockWorld) context.world()).serverWorld());
        assertSame(directory.worldAdapter(nether), context.world(), "one adapter per world keeps chunk tickets together");
    }

    @Test
    void fabricBlockWorldRefusesWorldChangesOffTheServerThread() throws Exception {
        FabricBlockWorld blockWorld = new FabricBlockWorld(world);
        Throwable[] failure = new Throwable[1];
        Thread other = new Thread(() -> {
            try {
                blockWorld.setBlock(new Vec3i(0, 64, 0), "minecraft:stone");
            } catch (Throwable ex) {
                failure[0] = ex;
            }
        });
        other.start();
        other.join();
        assertInstanceOf(IllegalStateException.class, failure[0]);
    }

    @Test
    void playerDirectoryTracksLivePosition() {
        FabricPlayerDirectory directory = new FabricPlayerDirectory(server);
        player.setBlockPos(new net.minecraft.util.math.BlockPos(100, 75, -200));

        Optional<PlayerContext> context = directory.find("Alice");
        assertTrue(context.isPresent());
        assertEquals(new Vec3i(100, 75, -200), context.get().position());
    }

    @Test
    void playerDirectoryResolvesDeterministicallyWithMultiplePlayers() {
        ServerPlayerEntity bob = new ServerPlayerEntity(server, world, UUID.randomUUID(), "Bob");
        server.getPlayerManager().addPlayer(bob);

        FabricPlayerDirectory directory = new FabricPlayerDirectory(server);
        // When null, picks deterministically by name: Alice comes before Bob
        Optional<PlayerContext> defaultPlayer = directory.find(null);
        assertTrue(defaultPlayer.isPresent());
        assertEquals("Alice", defaultPlayer.get().name());

        // Explicit lookup
        Optional<PlayerContext> bobPlayer = directory.find("Bob");
        assertTrue(bobPlayer.isPresent());
        assertEquals("Bob", bobPlayer.get().name());
    }

    @Test
    void serverRecordingCoordinatorStartsStopsAndAutoStops() {
        ServerRecordingCoordinator coordinator = new ServerRecordingCoordinator(server);

        RecordingStatus initial = coordinator.getStatus(player.getUuid());
        assertTrue(initial.available());
        assertFalse(initial.recording());

        RecordingStatus started = coordinator.startRecording(player.getUuid(), "Alice");
        assertTrue(started.recording());
        coordinator.setAutoStop(player.getUuid(), true);

        // Build completed should trigger auto-stop
        JobProgress job = new JobProgress(1, player.getUuid(), "house", JobKind.BUILD, JobState.COMPLETED, 10, 10, 100, 0, 5, false, null);
        coordinator.buildListener().onJobFinished(job);
        RecordingStatus stopped = coordinator.getStatus(player.getUuid());
        assertFalse(stopped.recording());
    }
}
