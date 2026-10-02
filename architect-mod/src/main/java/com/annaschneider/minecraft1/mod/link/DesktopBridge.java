package com.annaschneider.minecraft1.mod.link;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBlock;
import com.annaschneider.minecraft1.instantbuilder.TemplateRegistry;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.scene.SceneCompiler;
import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;
import com.annaschneider.minecraft1.largebuild.scene.ScenePreview;
import com.annaschneider.minecraft1.largebuild.scene.SceneRegion;
import com.annaschneider.minecraft1.link.BuildMode;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.link.JobStatus;
import com.annaschneider.minecraft1.link.LinkConnection;
import com.annaschneider.minecraft1.link.LinkInfo;
import com.annaschneider.minecraft1.link.LinkMessage;
import com.annaschneider.minecraft1.link.LinkProtocol;
import com.annaschneider.minecraft1.link.LinkRequest;
import com.annaschneider.minecraft1.link.LinkServer;
import com.annaschneider.minecraft1.link.PlanSummary;
import com.annaschneider.minecraft1.link.RegionBox;
import com.annaschneider.minecraft1.link.ServerInfo;
import com.annaschneider.minecraft1.link.RecordingStatus;
import com.annaschneider.minecraft1.mod.command.ArchitectCommandEngine;
import com.annaschneider.minecraft1.mod.command.CommandResult;
import com.annaschneider.minecraft1.mod.recording.ServerRecordingCoordinator;
import com.annaschneider.minecraft1.mod.runtime.ArchitectConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Connects the Windows desktop app to the {@link ArchitectCommandEngine}. Platform-neutral: the Fabric adapter creates
 * it next to the engine, calls {@link #start} when the server starts, {@link #tick} at the end of every server tick and
 * {@link #close} when the server stops. Requests are executed on the server thread inside {@link #tick}, reusing the
 * same validation and messages as the {@code /architect} commands.
 */
public final class DesktopBridge implements AutoCloseable {
    /** Progress events are pushed at most every this many ticks (0.5 s at 20 TPS). */
    public static final int PROGRESS_INTERVAL_TICKS = 10;
    public static final String UPLOAD_FOLDER = "desktop";
    private static final int MAX_REQUESTS_PER_TICK = 8;
    private static final int MAX_REGION_BOXES = 8_000;

    private final ArchitectCommandEngine engine;
    private final PlayerDirectory players;
    private final String modVersion;
    private final SceneCompiler compiler = new SceneCompiler();
    private final List<String> templates;
    private final ServerRecordingCoordinator recordingCoordinator;
    private final Map<Long, JobStatus> lastSent = new HashMap<>();
    private final Map<Long, CameraNpcSettings> desiredSettings = new HashMap<>();
    private final Map<Long, UUID> appliedSettings = new HashMap<>();
    private LinkServer server;
    private Path linkFile;
    private long ticks;

    public DesktopBridge(ArchitectCommandEngine engine, PlayerDirectory players, String modVersion) {
        this(engine, players, modVersion, new ServerRecordingCoordinator(null));
    }

    public DesktopBridge(ArchitectCommandEngine engine, PlayerDirectory players, String modVersion, ServerRecordingCoordinator recordingCoordinator) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.players = Objects.requireNonNull(players, "players");
        this.modVersion = modVersion == null ? "dev" : modVersion;
        this.recordingCoordinator = Objects.requireNonNull(recordingCoordinator, "recordingCoordinator");
        this.templates = new TemplateRegistry().ids().stream().sorted().toList();
        this.engine.queue().addListener(recordingCoordinator.buildListener());
    }

    public ServerRecordingCoordinator recordingCoordinator() {
        return recordingCoordinator;
    }

    /**
     * Listens on {@code 127.0.0.1:port} (0 = any free port) and writes {@code desktop-link.json} with the port and a
     * fresh random token into the engine's data folder, where the desktop app finds it.
     */
    public LinkInfo start(int port) throws IOException {
        if (server != null) {
            throw new IllegalStateException("The desktop link is already running.");
        }
        LinkInfo info = LinkInfo.create(port, modVersion);
        LinkServer created = new LinkServer(port, info.token());
        try {
            created.start();
        } catch (IOException busy) {
            // e.g. a second Minecraft instance: any free port works because the desktop app reads it from the link file
            created = new LinkServer(0, info.token());
            created.start();
        }
        info = info.withPort(created.port());
        Path file = engine.dataRoot().resolve(LinkProtocol.LINK_FILE_NAME);
        try {
            info.write(file);
        } catch (IOException ex) {
            created.close();
            throw ex;
        }
        server = created;
        linkFile = file;
        return info;
    }

    public Optional<Path> linkFile() {
        return Optional.ofNullable(linkFile);
    }

    public int connectedClients() {
        return server == null ? 0 : server.connections().size();
    }

    /** Server thread: executes queued desktop requests and pushes progress updates. */
    public void tick() {
        if (server == null) {
            return;
        }
        server.drain(this::handle, MAX_REQUESTS_PER_TICK);
        for (LinkConnection connection : server.connections()) {
            CameraNpcSettings desired = desiredSettings.get(connection.id());
            if (desired != null) {
                var player = players.find(connection.player());
                if (player.isEmpty()) {
                    appliedSettings.remove(connection.id());
                } else if (!player.get().id().equals(appliedSettings.get(connection.id()))) {
                    engine.applyCameraNpcSettings(player.get().id(), desired);
                    appliedSettings.put(connection.id(), player.get().id());
                }
            }
        }
        List<Long> connected = server.connections().stream().map(LinkConnection::id).toList();
        desiredSettings.keySet().retainAll(connected);
        appliedSettings.keySet().retainAll(connected);
        if (++ticks % PROGRESS_INTERVAL_TICKS == 0) {
            pushProgress();
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
            server = null;
        }
        if (linkFile != null) {
            try {
                Files.deleteIfExists(linkFile);
            } catch (IOException ignored) {
                // a stale file only makes the desktop app show "Minecraft is not reachable"
            }
            linkFile = null;
        }
        lastSent.clear();
        desiredSettings.clear();
        appliedSettings.clear();
    }

    LinkMessage handle(LinkConnection connection, LinkRequest request) {
        return switch (request.type()) {
            case HELLO, STATUS -> status(connection);
            case UPLOAD_IMAGE -> upload(request);
            case PLAN -> plan(connection, request);
            case PREVIEW -> request.mode() == BuildMode.TEMPLATE
                ? LinkMessage.ok(null, "Preview of template '" + request.template() + "'.").withPlan(templateSummary(request.template()))
                : LinkMessage.ok(null, "Preview of plan '" + request.planId() + "'.").withPlan(planSummary(engine.loadPlan(request.planId())));
            case BUILD -> command(connection, request.mode() == BuildMode.TEMPLATE
                ? "build " + request.template()
                : "image build " + request.planId());
            case PAUSE -> command(connection, "pause");
            case RESUME -> command(connection, "resume");
            case CANCEL -> command(connection, "cancel");
            case UNDO -> command(connection, "undo");
            case RECORD_START -> recordStart(connection);
            case RECORD_STOP -> recordStop(connection);
            case RECORD_STATUS -> recordStatus(connection);
            case CAMERA -> camera(connection, request);
            case SETTINGS -> settings(connection, request);
        };
    }

    private LinkMessage settings(LinkConnection connection, LinkRequest request) {
        CameraNpcSettings raw = request.settings();
        CameraNpcSettings bounded = new CameraNpcSettings(raw.cameraEnabled(), raw.npcEnabled(), raw.maxNpcs(),
            raw.cameraHeight(), raw.rotationSpeed());
        desiredSettings.put(connection.id(), bounded);
        var player = players.find(connection.player());
        if (player.isEmpty()) {
            appliedSettings.remove(connection.id());
            return LinkMessage.ok(null, "No world/player yet; settings remain saved in the desktop app.");
        }
        appliedSettings.put(connection.id(), player.get().id());
        return LinkMessage.ok(null, engine.applyCameraNpcSettings(player.get().id(), bounded));
    }

    /** Called when Minecraft replaces a player connection before the next desktop request. */
    public void reapplySettings(UUID playerId) {
        appliedSettings.values().removeIf(playerId::equals);
    }

    /** Camera mode and/or NPC toggle; both are plain {@code /architect} commands run for the connected player. */
    private LinkMessage camera(LinkConnection connection, LinkRequest request) {
        LinkMessage response = null;
        if (request.camera() != null) {
            response = command(connection, "camera " + request.camera().toLowerCase(java.util.Locale.ROOT));
            if (!response.isOk()) {
                return response;
            }
        }
        if (request.npc() != null) {
            LinkMessage npc = command(connection, "npc " + (request.npc() ? "on" : "off"));
            response = response == null || !npc.isOk() ? npc
                : LinkMessage.ok(null, response.message() + " " + npc.message());
        }
        return response == null ? LinkMessage.ok(null, "Nothing to change.") : response;
    }

    private LinkMessage recordStart(LinkConnection connection) {
        Optional<PlayerContext> player = players.find(connection.player());
        java.util.UUID playerId = player.map(PlayerContext::id).orElse(null);
        String playerName = player.map(PlayerContext::name).orElse(null);
        RecordingStatus status = recordingCoordinator.startRecording(playerId, playerName);
        return LinkMessage.ok(null, status.message()).withRecording(status);
    }

    private LinkMessage recordStop(LinkConnection connection) {
        Optional<PlayerContext> player = players.find(connection.player());
        java.util.UUID playerId = player.map(PlayerContext::id).orElse(null);
        String playerName = player.map(PlayerContext::name).orElse(null);
        RecordingStatus status = recordingCoordinator.stopRecording(playerId, playerName);
        return LinkMessage.ok(null, status.message()).withRecording(status);
    }

    private LinkMessage recordStatus(LinkConnection connection) {
        Optional<PlayerContext> player = players.find(connection.player());
        java.util.UUID playerId = player.map(PlayerContext::id).orElse(null);
        RecordingStatus status = recordingCoordinator.getStatus(playerId);
        return LinkMessage.ok(null, status.message()).withRecording(status);
    }

    private LinkMessage status(LinkConnection connection) {
        Optional<PlayerContext> player = players.find(connection.player());
        ServerInfo info = new ServerInfo(modVersion, LinkProtocol.VERSION, player.map(PlayerContext::name).orElse(null),
            templates, ArchitectConfig.MAX_IMAGE_SCALE, LinkProtocol.MAX_IMAGE_BYTES);
        String message = player.map(p -> "Connected to Minecraft as " + p.name() + ".")
            .orElseGet(() -> missingPlayerMessage(connection) + " You can still prepare plans.");
        java.util.UUID playerId = player.map(PlayerContext::id).orElse(null);
        RecordingStatus recStatus = recordingCoordinator.getStatus(playerId);
        LinkMessage response = LinkMessage.ok(null, message).withServer(info).withRecording(recStatus);
        return player.flatMap(p -> engine.queue().progress(p.id())).map(p -> response.withJob(toStatus(p))).orElse(response);
    }

    private LinkMessage upload(LinkRequest request) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(request.data());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Image data is not valid base64.");
        }
        if (bytes.length == 0 || bytes.length > LinkProtocol.MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("The image must be between 1 byte and "
                + LinkProtocol.MAX_IMAGE_BYTES / (1024 * 1024) + " MiB.");
        }
        String extension = LinkProtocol.extension(request.fileName());
        String baseName = request.fileName().substring(0, request.fileName().length() - extension.length() - 1);
        String fileName = LinkProtocol.slug(baseName, "image") + "." + extension;
        Path folder = engine.dataRoot().resolve("uploads").resolve(UPLOAD_FOLDER);
        Path target = folder.resolve(fileName);
        try {
            Files.createDirectories(folder);
            Path temp = folder.resolve(fileName + ".part");
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save the image: " + ex.getMessage(), ex);
        }
        String source = "uploads/" + UPLOAD_FOLDER + "/" + fileName;
        return LinkMessage.ok(null, "Image saved as " + source + " (" + bytes.length / 1024 + " KiB).").withSource(source);
    }

    private LinkMessage plan(LinkConnection connection, LinkRequest request) {
        String source = request.source() != null && !request.source().isBlank()
            ? request.source()
            : "placeholder:" + LinkProtocol.slug(request.prompt(), "prompt");
        CommandResult result = engine.execute(anyPlayerId(connection), null,
            "/architect image plan " + source + " " + request.planId() + " " + request.scale());
        if (!result.success()) {
            return LinkMessage.error(null, result.message());
        }
        return LinkMessage.ok(null, result.message()).withPlan(planSummary(engine.loadPlan(request.planId())));
    }

    private LinkMessage command(LinkConnection connection, String arguments) {
        PlayerContext player = players.find(connection.player())
            .orElseThrow(() -> new IllegalStateException(missingPlayerMessage(connection)));
        CommandResult result = engine.execute(player.id(), player.world(), player.position(), "/architect " + arguments);
        LinkMessage response = result.success() ? LinkMessage.ok(null, result.message()) : LinkMessage.error(null, result.message());
        return engine.queue().progress(player.id()).map(p -> response.withJob(toStatus(p))).orElse(response);
    }

    /** Planning does not touch the world, so it works before a player joins (the plan is not tied to a player). */
    private java.util.UUID anyPlayerId(LinkConnection connection) {
        return players.find(connection.player()).map(PlayerContext::id)
            .orElseGet(() -> java.util.UUID.nameUUIDFromBytes(("architect-desktop-" + connection.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void pushProgress() {
        Set<Long> open = new HashSet<>();
        for (LinkConnection connection : server.connections()) {
            open.add(connection.id());
            Optional<JobStatus> status = players.find(connection.player())
                .flatMap(player -> engine.queue().progress(player.id()))
                .map(DesktopBridge::toStatus);
            if (status.isPresent() && !status.get().equals(lastSent.get(connection.id()))) {
                lastSent.put(connection.id(), status.get());
                java.util.UUID playerId = players.find(connection.player()).map(PlayerContext::id).orElse(null);
                connection.send(LinkMessage.progress(status.get()).withRecording(recordingCoordinator.getStatus(playerId)));
            }
        }
        lastSent.keySet().retainAll(open);
    }

    PlanSummary planSummary(ScenePlan plan) {
        ScenePreview preview = ScenePreview.of(plan, compiler.compile(plan, engine.queue().settings().maxSectionsPerJob()));
        List<RegionBox> boxes = new ArrayList<>(Math.min(plan.regions().size(), MAX_REGION_BOXES));
        for (SceneRegion region : plan.regions()) {
            if (boxes.size() >= MAX_REGION_BOXES) {
                break;
            }
            Bounds bounds = compiler.place(region).bounds();
            boxes.add(new RegionBox(region.type().id(), bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ()));
        }
        StringBuilder text = new StringBuilder(preview.describe());
        plan.notes().forEach(note -> text.append('\n').append("- ").append(note));
        Bounds bounds = preview.bounds();
        return new PlanSummary(plan.id(), plan.title(), text.toString(), bounds.sizeX(), bounds.sizeY(), bounds.sizeZ(),
            preview.estimatedBlocks(), boxes);
    }

    PlanSummary templateSummary(String template) {
        Blueprint blueprint = new TemplateRegistry().create(template).orElseThrow(() -> new IllegalArgumentException(
            "Unknown template '" + template + "'. Use " + String.join(", ", templates) + "."));
        Map<Long, BlueprintBlock> topBlocks = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlueprintBlock block : blueprint.blocks()) {
            var p = block.position();
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
            maxZ = Math.max(maxZ, p.z());
            topBlocks.merge(((long) p.x() << 32) | (p.z() & 0xFFFFFFFFL), block,
                (a, b) -> b.position().y() > a.position().y() ? b : a);
        }
        List<RegionBox> boxes = new ArrayList<>();
        for (BlueprintBlock block : topBlocks.values()) {
            if (boxes.size() >= MAX_REGION_BOXES) {
                break;
            }
            var p = block.position();
            boxes.add(new RegionBox(block.blockId().replaceFirst("^minecraft:", ""), p.x(), p.z(), p.x(), p.z()));
        }
        int blocks = blueprint.blocks().size();
        String text = blocks == 0
            ? "Template '" + template + "' is empty."
            : String.format(Locale.ROOT, "Template '%s': %,d blocks%nSize %dx%dx%d, built at your position.", template, blocks,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        return blocks == 0
            ? new PlanSummary(template, template, text, 0, 0, 0, 0, boxes)
            : new PlanSummary(template, template, text, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, blocks, boxes);
    }

    static JobStatus toStatus(JobProgress progress) {
        return new JobStatus(progress.jobId(), progress.name(), progress.kind().name().toLowerCase(Locale.ROOT),
            progress.state().name().toLowerCase(Locale.ROOT), Math.round(progress.percent() * 10) / 10.0,
            progress.unitsDone(), progress.unitsTotal(), progress.blocksChanged(), progress.waitingForChunks(),
            progress.message());
    }

    private static String missingPlayerMessage(LinkConnection connection) {
        return connection.player() == null
            ? "No player is in a world. Open a world in Minecraft (or join the server) and try again."
            : "Player '" + connection.player() + "' is not online. Join the world or change the player name in the app.";
    }
}
