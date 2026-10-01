package com.annaschneider.minecraft1.mod;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.link.LinkInfo;
import com.annaschneider.minecraft1.link.RecordingStatus;
import com.annaschneider.minecraft1.mod.command.CommandResult;
import com.annaschneider.minecraft1.mod.fabric.FabricBlockWorld;
import com.annaschneider.minecraft1.mod.fabric.FabricPlayerDirectory;
import com.annaschneider.minecraft1.mod.recording.RecordingChannels;
import com.annaschneider.minecraft1.mod.recording.ServerRecordingCoordinator;
import com.annaschneider.minecraft1.mod.runtime.ArchitectServerRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

public final class ArchitectFabricMod implements ModInitializer {
    public static final String MOD_ID = "architect";
    public static final String ROOT_COMMAND = "architect";
    private static final Logger LOGGER = Logger.getLogger("com.annaschneider.minecraft1.mod");

    private MinecraftServer currentServer;
    private ArchitectServerRuntime runtime;
    private FabricPlayerDirectory playerDirectory;

    @Override
    public void onInitialize() {
        LOGGER.info("[Architect] Initializing Architect Fabric mod...");

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

        // Hook server lifecycle
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);

        // Hook server tick
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);

        // Hook client status networking packets
        ServerPlayNetworking.registerGlobalReceiver(RecordingChannels.RECORD_STAT_CHANNEL, (server, player, handler, buf, responseSender) -> {
            String json = buf.readString();
            server.execute(() -> {
                if (runtime != null) {
                    runtime.recordingCoordinator().onClientStatusReceived(player, json);
                }
            });
        });
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal(ROOT_COMMAND)
                .executes(this::executeRoot)
                .then(CommandManager.argument("command", StringArgumentType.greedyString())
                    .executes(this::executeSubcommand))
        );
    }

    private int executeRoot(CommandContext<ServerCommandSource> ctx) {
        return handleCommand(ctx.getSource(), "help");
    }

    private int executeSubcommand(CommandContext<ServerCommandSource> ctx) {
        String input = StringArgumentType.getString(ctx, "command");
        return handleCommand(ctx.getSource(), input);
    }

    private int handleCommand(ServerCommandSource source, String input) {
        if (runtime == null) {
            source.sendError(Text.literal("Architect runtime is not active."));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Architect commands must be run by an in-game player."));
            return 0;
        }

        // Special handling for in-game recording command: /architect record <start|stop|status>
        String trimmed = input.trim();
        if (trimmed.startsWith("record")) {
            return handleRecordCommand(source, player, trimmed);
        }

        CommandResult result = runEngine(player, input);
        if (result.success()) {
            source.sendFeedback(() -> Text.literal(result.message()), false);
            return 1;
        } else {
            source.sendError(Text.literal(result.message()));
            return 0;
        }
    }

    /** Runs the shared command engine in the player's current world at their current position. */
    private CommandResult runEngine(ServerPlayerEntity player, String input) {
        try {
            FabricBlockWorld world = playerDirectory.worldAdapter(player.getServerWorld());
            Vec3i origin = new Vec3i(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            return runtime.engine().execute(player.getUuid(), world, origin, "/architect " + input);
        } catch (RuntimeException ex) {
            LOGGER.warning("[Architect] /architect " + input + " failed: " + ex);
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new CommandResult(false, "Architect command failed: " + reason);
        }
    }

    private int handleRecordCommand(ServerCommandSource source, ServerPlayerEntity player, String input) {
        String[] parts = input.split("\\s+");
        String action = parts.length > 1 ? parts[1].toLowerCase() : "status";
        ServerRecordingCoordinator coordinator = runtime.recordingCoordinator();
        RecordingStatus status = switch (action) {
            case "start" -> coordinator.startRecording(player.getUuid(), player.getName().getString());
            case "stop" -> coordinator.stopRecording(player.getUuid(), player.getName().getString());
            case "status" -> coordinator.getStatus(player.getUuid());
            default -> new RecordingStatus(false, false, "ERROR", null, null, "Usage: /architect record <start|stop|status>");
        };
        if (status.available() || status.recording()) {
            source.sendFeedback(() -> Text.literal(status.describe()), false);
            return 1;
        } else {
            source.sendError(Text.literal(status.describe()));
            return 0;
        }
    }

    private void onServerStarting(MinecraftServer server) {
        LOGGER.info("[Architect] Minecraft server starting. Initializing runtime...");
        this.currentServer = server;
        this.playerDirectory = new FabricPlayerDirectory(server);
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("architect");
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("1.0.0");
        ServerRecordingCoordinator recordingCoordinator = new ServerRecordingCoordinator(server);
        this.runtime = new ArchitectServerRuntime(configDir, playerDirectory, version, recordingCoordinator);

        Optional<LinkInfo> link = runtime.startLink();
        if (link.isPresent()) {
            LOGGER.info("[Architect] Desktop link started on 127.0.0.1:" + link.get().port()
                + ". Link file written to config/architect/desktop-link.json");
        } else {
            LOGGER.warning("[Architect] " + runtime.linkError().orElse("Desktop link failed to start."));
        }
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("[Architect] Server stopping. Cleaning up Architect resources...");
        shutdown();
    }

    private void onServerStopped(MinecraftServer server) {
        shutdown();
    }

    /** Closes the desktop link (deleting desktop-link.json) and releases every chunk ticket; safe to call twice. */
    private void shutdown() {
        if (runtime != null) {
            try {
                runtime.close();
            } catch (RuntimeException ex) {
                LOGGER.warning("[Architect] Error while closing the runtime: " + ex);
            }
            runtime = null;
        }
        if (playerDirectory != null) {
            playerDirectory.releaseAllChunkTickets();
            playerDirectory = null;
        }
        this.currentServer = null;
    }

    private void onEndServerTick(MinecraftServer server) {
        if (runtime == null || playerDirectory == null || server != currentServer) {
            return;
        }
        try {
            // Build jobs remember the world they were started in; the overworld is only the fallback for unbound jobs.
            runtime.tick(playerDirectory.worldAdapter(server.getOverworld()));
        } catch (RuntimeException ex) {
            LOGGER.warning("[Architect] Tick failed: " + ex);
        }
    }

    public ArchitectServerRuntime runtime() {
        return runtime;
    }

    public FabricPlayerDirectory playerDirectory() {
        return playerDirectory;
    }
}
