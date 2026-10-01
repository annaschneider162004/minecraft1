package com.annaschneider.minecraft1.mod;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.link.LinkClient;
import com.annaschneider.minecraft1.link.LinkInfo;
import com.annaschneider.minecraft1.link.LinkMessage;
import com.annaschneider.minecraft1.link.LinkProtocol;
import com.annaschneider.minecraft1.mod.fabric.FabricBlockWorld;
import com.annaschneider.minecraft1.mod.recording.RecordingChannels;
import com.annaschneider.minecraft1.mod.runtime.BlockCatalog;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives {@link ArchitectFabricMod} through the (stubbed) Fabric events the way Minecraft does. */
class ArchitectFabricModTest {
    @TempDir
    Path configDir;
    private ArchitectFabricMod mod;
    private CommandDispatcher<ServerCommandSource> dispatcher;
    private MinecraftServer server;
    private ServerWorld world;
    private ServerPlayerEntity alice;

    @BeforeAll
    static void registerVanillaBlocks() {
        for (String id : BlockCatalog.supportedIds()) {
            Identifier identifier = Identifier.tryParse(id);
            if (!Registries.BLOCK.containsId(identifier)) {
                Registries.registerBlock(identifier.getPath(), new Block());
            }
        }
    }

    @BeforeEach
    void setUp() {
        CommandRegistrationCallback.EVENT.clear();
        ServerLifecycleEvents.SERVER_STARTING.clear();
        ServerLifecycleEvents.SERVER_STARTED.clear();
        ServerLifecycleEvents.SERVER_STOPPING.clear();
        ServerLifecycleEvents.SERVER_STOPPED.clear();
        ServerTickEvents.END_SERVER_TICK.clear();
        ServerPlayNetworking.clearReceivers();
        FabricLoader loader = new FabricLoader();
        loader.setConfigDir(configDir);
        loader.registerMod(ArchitectFabricMod.MOD_ID, "2.0.0-test");
        FabricLoader.setInstance(loader);

        mod = new ArchitectFabricMod();
        mod.onInitialize();
        dispatcher = new CommandDispatcher<>();
        CommandRegistrationCallback.EVENT.handlers()
            .forEach(callback -> callback.register(dispatcher, null, CommandManager.RegistrationEnvironment.ALL));

        server = newServer();
        world = server.getOverworld();
        alice = new ServerPlayerEntity(server, world, UUID.randomUUID(), "Alice");
        alice.setBlockPos(new BlockPos(8, 64, 8));
        server.getPlayerManager().addPlayer(alice);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            stop(server);
        }
        FabricLoader.setInstance(new FabricLoader());
    }

    @Test
    void registersCommandLifecycleTickAndNetworkingHooksExactlyOnce() {
        assertEquals(1, CommandRegistrationCallback.EVENT.handlers().size());
        assertEquals(1, ServerLifecycleEvents.SERVER_STARTING.handlers().size());
        assertEquals(1, ServerTickEvents.END_SERVER_TICK.handlers().size());
        assertTrue(dispatcher.getRegisteredCommands().containsKey(ArchitectFabricMod.ROOT_COMMAND));
        assertNotNull(ServerPlayNetworking.getReceiver(RecordingChannels.RECORD_STAT_CHANNEL));
        assertNull(mod.runtime(), "no runtime before a world is opened");
    }

    @Test
    void serverStartWritesLinkFileInConfigArchitectAndStopRemovesIt() throws Exception {
        start(server);
        assertNotNull(mod.runtime());
        Path linkFile = configDir.resolve("architect").resolve(LinkProtocol.LINK_FILE_NAME);
        assertTrue(Files.exists(linkFile), "desktop-link.json must be written to <config>/architect");
        LinkInfo info = LinkInfo.read(linkFile);
        assertEquals("127.0.0.1", info.host());
        assertEquals("2.0.0-test", info.modVersion());
        assertFalse(info.token().isBlank());

        stop(server);
        server = null;
        assertNull(mod.runtime());
        assertFalse(Files.exists(linkFile), "the link file is removed when the world closes");
    }

    @Test
    void desktopAppConnectsAsTheRealPlayer() throws Exception {
        start(server);
        LinkInfo info = LinkInfo.read(configDir.resolve("architect").resolve(LinkProtocol.LINK_FILE_NAME));
        CompletableFuture<LinkClient> connecting = CompletableFuture.supplyAsync(() -> {
            try {
                return LinkClient.connect(info, "test", null, new LinkClient.Listener() {
                    @Override
                    public void onEvent(LinkMessage message) {
                    }

                    @Override
                    public void onDisconnected(String reason) {
                    }
                }, Duration.ofSeconds(10));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!connecting.isDone() && System.nanoTime() < deadline) {
            tick(server, 1); // requests from the desktop app are answered on the server thread
            Thread.sleep(5);
        }
        try (LinkClient client = connecting.get(1, TimeUnit.SECONDS)) {
            assertEquals("Alice", client.serverInfo().player());
        }
    }

    @Test
    void architectBuildHousePlacesBlocksInThePlayersWorldAndReleasesChunkTickets() throws CommandSyntaxException {
        start(server);
        ServerCommandSource source = new ServerCommandSource(server, world, alice, new Vec3d(8, 64, 8));
        assertEquals(1, dispatcher.execute("architect build house", source), source.getLastError());
        assertNotNull(source.getLastFeedback());

        tick(server, 200);
        assertTrue(countPlacedBlocks(world) > 0, "the house is built in the real world");
        assertEquals(0, world.getChunkManager().getTicketCount(ChunkTicketType.FORCED),
            "chunk tickets are released once the build completes");

        assertEquals(1, dispatcher.execute("architect undo", source), source.getLastError());
        tick(server, 200);
        assertEquals(0, countPlacedBlocks(world), "undo restores the previous blocks");
    }

    @Test
    void commandFailuresBecomeFeedbackInsteadOfCrashes() throws CommandSyntaxException {
        ServerCommandSource source = new ServerCommandSource(server, world, alice, new Vec3d(8, 64, 8));
        assertEquals(0, dispatcher.execute("architect help", source));
        assertTrue(source.getLastError().contains("not active"), source.getLastError());

        start(server);
        assertEquals(1, dispatcher.execute("architect", source), "bare /architect shows help");
        assertEquals(0, dispatcher.execute("architect build no_such_template", source));
        assertNotNull(source.getLastError());

        ServerCommandSource console = new ServerCommandSource(server, world, null, new Vec3d(0, 0, 0));
        assertEquals(0, dispatcher.execute("architect build house", console));
        assertTrue(console.getLastError().contains("player"), console.getLastError());
    }

    @Test
    void reopeningAWorldCreatesAFreshRuntime() {
        start(server);
        Object first = mod.runtime();
        stop(server);

        server = newServer();
        server.getPlayerManager().addPlayer(new ServerPlayerEntity(server, server.getOverworld(), UUID.randomUUID(), "Bob"));
        start(server);
        assertNotNull(mod.runtime());
        assertNotSame(first, mod.runtime());
        assertTrue(Files.exists(configDir.resolve("architect").resolve(LinkProtocol.LINK_FILE_NAME)));
    }

    @Test
    void stoppingMidBuildReleasesEveryChunkTicket() throws CommandSyntaxException {
        start(server);
        ServerCommandSource source = new ServerCommandSource(server, world, alice, new Vec3d(8, 64, 8));
        assertEquals(1, dispatcher.execute("architect build castle", source), source.getLastError());
        tick(server, 2);
        stop(server);
        server = null;
        assertEquals(0, world.getChunkManager().getTicketCount(ChunkTicketType.FORCED));
    }

    private static MinecraftServer newServer() {
        MinecraftServer created = new MinecraftServer();
        // pretend the spawn area is loaded, like in a real world after it opened
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                created.getOverworld().getChunkManager().setChunkLoaded(cx, cz, true);
            }
        }
        return created;
    }

    private static int countPlacedBlocks(ServerWorld world) {
        FabricBlockWorld blocks = new FabricBlockWorld(world);
        int count = 0;
        for (int x = -16; x < 48; x++) {
            for (int y = 60; y < 100; y++) {
                for (int z = -16; z < 48; z++) {
                    if (!"minecraft:air".equals(blocks.getBlock(new Vec3i(x, y, z)))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void start(MinecraftServer server) {
        ServerLifecycleEvents.SERVER_STARTING.handlers().forEach(handler -> handler.onServerStarting(server));
    }

    private static void stop(MinecraftServer server) {
        ServerLifecycleEvents.SERVER_STOPPING.handlers().forEach(handler -> handler.onServerStopping(server));
        ServerLifecycleEvents.SERVER_STOPPED.handlers().forEach(handler -> handler.onServerStopped(server));
    }

    private static void tick(MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ServerTickEvents.END_SERVER_TICK.handlers().forEach(handler -> handler.onEndTick(server));
        }
    }
}
