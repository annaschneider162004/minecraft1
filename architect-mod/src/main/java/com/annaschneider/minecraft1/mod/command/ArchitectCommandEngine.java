package com.annaschneider.minecraft1.mod.command;

import com.annaschneider.minecraft1.aifoundation.LocalBoundedWorldCreator;
import com.annaschneider.minecraft1.aifoundation.WorldCreationRequest;
import com.annaschneider.minecraft1.buildassistant.ShapeBuilderService;
import com.annaschneider.minecraft1.buildtransformer.BuildTransformerService;
import com.annaschneider.minecraft1.buildtransformer.TransformOperation;
import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintValidation;
import com.annaschneider.minecraft1.domain.MirrorAxis;
import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.instantbuilder.TemplateRegistry;
import com.annaschneider.minecraft1.largebuild.blueprint.BlueprintSource;
import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionedBlueprint;
import com.annaschneider.minecraft1.largebuild.camera.BuildCameraDirector;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.camera.CameraPath;
import com.annaschneider.minecraft1.largebuild.camera.DeterministicShotPlanner;
import com.annaschneider.minecraft1.largebuild.camera.ShotType;
import com.annaschneider.minecraft1.largebuild.engine.BuildQueue;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.largebuild.engine.JobProgress;
import com.annaschneider.minecraft1.largebuild.image.ImageReferenceResolver;
import com.annaschneider.minecraft1.largebuild.image.ImageToBlueprintPipeline;
import com.annaschneider.minecraft1.largebuild.image.PlanOptions;
import com.annaschneider.minecraft1.largebuild.persistence.BlueprintStore;
import com.annaschneider.minecraft1.largebuild.persistence.ScenePlanStore;
import com.annaschneider.minecraft1.largebuild.persistence.StoredBlueprint;
import com.annaschneider.minecraft1.largebuild.scene.RegionType;
import com.annaschneider.minecraft1.largebuild.scene.SceneCompiler;
import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;
import com.annaschneider.minecraft1.largebuild.scene.ScenePreview;
import com.annaschneider.minecraft1.largebuild.scene.SceneRegion;
import com.annaschneider.minecraft1.mod.runtime.ArchitectConfig;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.mod.runtime.BlockCatalog;
import com.annaschneider.minecraft1.mod.runtime.BlockWorld;
import com.annaschneider.minecraft1.mod.runtime.BlockWorldAccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Parses {@code /architect ...} commands and drives the large-build {@link BuildQueue}. Must be called from the server
 * thread (except blueprint export, which runs on a background thread and only touches files).
 */
public final class ArchitectCommandEngine implements AutoCloseable {
    /** Live cinematic camera of one client; supplied by the Fabric adapter, absent in headless tests. */
    public interface CameraControl {
        String setMode(UUID playerId, CameraMode mode);

        String describe(UUID playerId);

        void applySettings(UUID playerId, CameraNpcSettings settings);
    }

    /** Visible builder NPCs; supplied by the Fabric adapter, absent in headless tests. */
    public interface CrewControl {
        String setEnabled(boolean enabled);

        String describe();

        void applySettings(CameraNpcSettings settings);
    }

    public static final Vec3i DEFAULT_ORIGIN = new Vec3i(0, 1, 0);
    private static final int STRUCTURE_MAX_BLOCKS = 10_000;
    private static final int KINGDOM_RADIUS = 24;
    private static final String MEGA_TYPES = "palace|bridge|terrace|island|waterfall|cherry|garden|path|clouds";

    private final TemplateRegistry templates = new TemplateRegistry();
    private final ShapeBuilderService shapes = new ShapeBuilderService();
    private final BuildTransformerService transformer = new BuildTransformerService();
    private final LocalBoundedWorldCreator worldCreator = new LocalBoundedWorldCreator();
    private final SceneCompiler compiler = new SceneCompiler();
    private final Path dataRoot;
    private final BuildQueue queue;
    private final ScenePlanStore plans;
    private final BlueprintStore blueprints;
    private final ImageReferenceResolver images;
    private final ImageToBlueprintPipeline pipeline;
    private final BuildCameraDirector camera = new BuildCameraDirector(new DeterministicShotPlanner());
    private final Map<UUID, String> loadedPlans = new HashMap<>();
    private CameraControl cameraControl;
    private CrewControl crewControl;
    private final Map<String, CompletableFuture<Long>> exports = new ConcurrentHashMap<>();
    private final Map<String, String> exportErrors = new ConcurrentHashMap<>();
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "architect-blueprint-export");
        thread.setDaemon(true);
        return thread;
    });

    /** Uses {@code -Darchitect.dataDir} or a fresh temporary directory. */
    public ArchitectCommandEngine() {
        this(defaultDataRoot());
    }

    public ArchitectCommandEngine(Path dataRoot) {
        this(dataRoot, ArchitectConfig.buildSettings(dataRoot.resolve("journals")));
    }

    public ArchitectCommandEngine(Path dataRoot, BuildSettings settings) {
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dataRoot.resolve("uploads"));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create architect data directory " + this.dataRoot, ex);
        }
        this.queue = new BuildQueue(settings);
        this.queue.addListener(camera);
        this.plans = new ScenePlanStore(this.dataRoot.resolve("plans"));
        this.blueprints = new BlueprintStore(this.dataRoot.resolve("blueprints"),
            Math.min(ArchitectConfig.MAX_EXPORT_SECTIONS, settings.maxSectionsPerJob()));
        this.images = new ImageReferenceResolver(this.dataRoot.resolve("uploads"));
        this.pipeline = ImageToBlueprintPipeline.deterministic(images);
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public BuildQueue queue() {
        return queue;
    }

    /** Source of the server-to-client camera updates. */
    public BuildCameraDirector cameraDirector() {
        return camera;
    }

    public void setCameraControl(CameraControl control) {
        this.cameraControl = control;
    }

    public void setCrewControl(CrewControl control) {
        this.crewControl = control;
    }

    /** Apply desktop preferences on the server thread; headless/demo runtimes can report unavailable features. */
    public String applyCameraNpcSettings(UUID playerId, CameraNpcSettings settings) {
        if (playerId == null) {
            return "No player is in a world; settings remain saved in the desktop app.";
        }
        CameraNpcSettings bounded = new CameraNpcSettings(settings.cameraEnabled(), settings.npcEnabled(),
            settings.maxNpcs(), settings.cameraHeight(), settings.rotationSpeed());
        if (cameraControl != null) {
            cameraControl.applySettings(playerId, bounded);
        }
        if (crewControl != null) {
            crewControl.applySettings(bounded);
        }
        return cameraControl == null && crewControl == null
            ? "Camera and NPC features are unavailable in this runtime; settings remain saved."
            : "Camera and NPC settings applied.";
    }

    /** Loads a saved scene plan (throws {@link IllegalArgumentException} with a user-facing message if missing). */
    public ScenePlan loadPlan(String planId) {
        return plans.load(planId);
    }

    public CommandResult execute(UUID playerId, BlockWorld world, String rawCommand) {
        return execute(playerId, world, DEFAULT_ORIGIN, rawCommand);
    }

    /** {@code origin} is where builds are anchored (typically the player's block position). */
    public CommandResult execute(UUID playerId, BlockWorld world, Vec3i origin, String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return new CommandResult(false, "Command is empty. Use /architect help.");
        }
        String normalized = rawCommand.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] args = normalized.split("\\s+");
        if (args.length == 0 || !"architect".equalsIgnoreCase(args[0])) {
            return new CommandResult(false, "Unknown root command. Use /architect help.");
        }
        if (args.length == 1 || "help".equalsIgnoreCase(args[1])) {
            return help();
        }

        try {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "build" -> handleBuild(playerId, world, origin, args);
                case "shape" -> handleShape(playerId, world, origin, args);
                case "transform" -> handleTransform(playerId, world, origin, args);
                case "world" -> handleWorld(playerId, world, origin, args);
                case "mega" -> handleMega(playerId, world, origin, args);
                case "image" -> handleImage(playerId, world, origin, args);
                case "blueprint" -> handleBlueprint(playerId, world, origin, args);
                case "queue" -> handleQueue();
                case "progress" -> handleProgress(playerId);
                case "pause" -> handlePause(playerId, world);
                case "resume" -> handleResume(playerId);
                case "cancel" -> handleCancel(playerId, world);
                case "undo" -> handleUndo(playerId);
                case "camera" -> handleCamera(playerId, args);
                case "npc" -> handleNpc(args);
                default -> new CommandResult(false, "Unknown subcommand. Use /architect help.");
            };
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException ex) {
            return new CommandResult(false, ex.getMessage());
        }
    }

    public TickSummary tick(BlockWorld world) {
        BuildQueue.TickReport report = queue.tick(new BlockWorldAccess(world));
        return new TickSummary(report.blocksChanged(), report.completedJobs(), report.remainingJobs());
    }

    /** Waits for running blueprint exports (useful for tests and shutdown). */
    public boolean awaitExports(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (CompletableFuture<Long> future : List.copyOf(exports.values())) {
            try {
                future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.TimeoutException ex) {
                return false;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            } catch (java.util.concurrent.ExecutionException ex) {
                // recorded in exportErrors
            }
        }
        return true;
    }

    @Override
    public void close() {
        exportExecutor.shutdownNow();
    }

    private CommandResult handleBuild(UUID playerId, BlockWorld world, Vec3i origin, String[] args) {
        requireLength(args, 3, "Usage: /architect build <house|castle|temple|village>");
        Blueprint blueprint = templates.create(args[2].toLowerCase(Locale.ROOT)).orElseThrow(() ->
            new IllegalArgumentException("Unknown template. Use house, castle, temple, village."));
        return queueBuild(playerId, world, origin, blueprint, "Template queued: " + args[2], STRUCTURE_MAX_BLOCKS, 96);
    }

    private CommandResult handleShape(UUID playerId, BlockWorld world, Vec3i origin, String[] args) {
        requireLength(args, 3, "Usage: /architect shape <wall|sphere|column> ...");
        String shape = args[2].toLowerCase(Locale.ROOT);
        Blueprint blueprint = switch (shape) {
            case "wall" -> {
                requireLength(args, 6, "Usage: /architect shape wall <length> <height> <block>");
                yield shapes.wall(parseInt(args[3], "length"), parseInt(args[4], "height"), args[5]);
            }
            case "sphere" -> {
                requireLength(args, 5, "Usage: /architect shape sphere <radius> <block>");
                yield shapes.sphere(parseInt(args[3], "radius"), args[4]);
            }
            case "column" -> {
                requireLength(args, 5, "Usage: /architect shape column <height> <block>");
                yield shapes.column(parseInt(args[3], "height"), args[4]);
            }
            default -> throw new IllegalArgumentException("Unknown shape. Use wall, sphere, or column.");
        };
        return queueBuild(playerId, world, origin, blueprint, "Shape queued: " + shape, STRUCTURE_MAX_BLOCKS, 96);
    }

    private CommandResult handleTransform(UUID playerId, BlockWorld world, Vec3i origin, String[] args) {
        requireLength(args, 4, "Usage: /architect transform <upgrade|style|damage> <template>");
        TransformOperation op = TransformOperation.fromId(args[2]).orElseThrow(() ->
            new IllegalArgumentException("Unknown transform mode. Use upgrade, style, damage."));
        String template = args[3].toLowerCase(Locale.ROOT);
        Blueprint source = templates.create(template).orElseThrow(() ->
            new IllegalArgumentException("Unknown template. Use house, castle, temple, village."));
        Blueprint transformed = transformer.transform(op, template, source);
        return queueBuild(playerId, world, origin, transformed, "Transform queued: " + op.name().toLowerCase(Locale.ROOT) + " " + template, STRUCTURE_MAX_BLOCKS, 96);
    }

    private CommandResult handleWorld(UUID playerId, BlockWorld world, Vec3i origin, String[] args) {
        requireLength(args, 3, "Usage: /architect world <kingdom>");
        if (!"kingdom".equalsIgnoreCase(args[2])) {
            throw new IllegalArgumentException("Unsupported world preset. Use kingdom.");
        }
        WorldCreationRequest request = new WorldCreationRequest("fantasy kingdom", KINGDOM_RADIUS);
        Blueprint blueprint = worldCreator.createPlan(request).previewBlueprint();
        return queueBuild(playerId, world, origin, blueprint, "World preset queued: kingdom", worldMaxBlocks(request.maxRadius()), 96);
    }

    private CommandResult handleMega(UUID playerId, BlockWorld world, Vec3i origin, String[] args) {
        String usage = "Usage: /architect mega <" + MEGA_TYPES + "> [size] [rotation 0|90|180|270] [mirror none|x|z]";
        requireLength(args, 3, usage);
        RegionType type = RegionType.fromId(args[2]).orElseThrow(() ->
            new IllegalArgumentException("Unknown mega structure '" + args[2] + "'. Use " + MEGA_TYPES.replace('|', ',') + "."));
        int size = args.length > 3 ? parseInt(args[3], "size") : defaultMegaSize(type);
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive.");
        }
        int rotation = args.length > 4 ? parseRotation(args[4]) : 0;
        MirrorAxis mirror = args.length > 5 ? parseMirror(args[5]) : MirrorAxis.NONE;
        SceneRegion region = megaRegion(type, size, rotation, mirror);
        ScenePlan plan = new ScenePlan(ScenePlan.FORMAT_VERSION, "mega-" + type.id(), "Mega " + type.id(), "command",
            "command", "default", 0L, 1, List.of(region), List.of());
        ProceduralBlueprint blueprint = compiler.compile(plan, queue.settings().maxSectionsPerJob());
        ScenePreview preview = ScenePreview.of(plan, blueprint);
        JobProgress progress = submit(playerId, world, blueprint, snapToChunk(origin));
        return new CommandResult(true, String.format(Locale.ROOT,
            "Mega structure queued: %s size %d rotation %d mirror %s (%d sections, ~%,d blocks). Job #%d - use /architect progress.",
            type.id(), size, rotation, mirror.name().toLowerCase(Locale.ROOT), preview.sectionCount(), preview.estimatedBlocks(), progress.jobId()));
    }

    private CommandResult handleImage(UUID playerId, BlockWorld world, Vec3i origin, String[] args) {
        String usage = "Usage: /architect image <plan|load|preview|build|export|list> ...";
        requireLength(args, 3, usage);
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "plan" -> {
                requireLength(args, 5, "Usage: /architect image plan <uploads/<file>|placeholder:<name>> <planId> [scale 1.."
                    + ArchitectConfig.MAX_IMAGE_SCALE + "]");
                int scale = args.length > 5 ? parseInt(args[5], "scale") : 1;
                if (scale < 1 || scale > ArchitectConfig.MAX_IMAGE_SCALE) {
                    throw new IllegalArgumentException("scale must be in range 1.." + ArchitectConfig.MAX_IMAGE_SCALE + ".");
                }
                ScenePlan plan = pipeline.plan(args[3], new PlanOptions(args[4].toLowerCase(Locale.ROOT), scale));
                plans.save(plan);
                loadedPlans.put(playerId, plan.id());
                yield new CommandResult(true, "Plan '" + plan.id() + "' created from " + plan.source() + " with "
                    + plan.regions().size() + " regions (" + plan.provider() + "), saved and loaded. Next: /architect image preview");
            }
            case "load" -> {
                requireLength(args, 4, "Usage: /architect image load <planId>");
                ScenePlan plan = plans.load(args[3].toLowerCase(Locale.ROOT));
                loadedPlans.put(playerId, plan.id());
                yield new CommandResult(true, "Loaded plan '" + plan.id() + "' (" + plan.title() + ", " + plan.regions().size()
                    + " regions, scale " + plan.scale() + ").");
            }
            case "preview" -> {
                ScenePlan plan = plans.load(planIdArgument(playerId, args));
                ScenePreview preview = ScenePreview.of(plan, compiler.compile(plan, queue.settings().maxSectionsPerJob()));
                yield new CommandResult(true, preview.describe());
            }
            case "build" -> {
                ScenePlan plan = plans.load(planIdArgument(playerId, args));
                ProceduralBlueprint blueprint = compiler.compile(plan, queue.settings().maxSectionsPerJob());
                JobProgress progress = submit(playerId, world, blueprint, snapToChunk(origin));
                yield new CommandResult(true, "Image scene '" + plan.id() + "' queued as job #" + progress.jobId() + " ("
                    + blueprint.sectionCount() + " sections). Use /architect progress, /architect cancel or /architect undo.");
            }
            case "export" -> {
                String planId = planIdArgument(playerId, args);
                ScenePlan plan = plans.load(planId);
                ProceduralBlueprint blueprint = compiler.compile(plan, queue.settings().maxSectionsPerJob());
                BlockCatalog.requireSupported(blueprint.palette().blockIds());
                startExport(planId, blueprint);
                yield new CommandResult(true, "Exporting plan '" + planId + "' (" + blueprint.sectionCount()
                    + " sections) to blueprints/" + planId + ".mcab in the background. Check with /architect blueprint list.");
            }
            case "list" -> {
                List<String> ids = plans.list();
                yield new CommandResult(true, ids.isEmpty() ? "No saved plans. Create one with /architect image plan."
                    : "Saved plans: " + String.join(", ", ids));
            }
            default -> throw new IllegalArgumentException(usage);
        };
    }

    private CommandResult handleBlueprint(UUID playerId, BlockWorld world, Vec3i origin, String[] args) {
        String usage = "Usage: /architect blueprint <list|build <name>>";
        requireLength(args, 3, usage);
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<String> lines = new ArrayList<>();
                List<String> saved = blueprints.list();
                lines.add(saved.isEmpty() ? "No saved blueprints." : "Saved blueprints: " + String.join(", ", saved));
                exports.forEach((name, future) -> {
                    if (!future.isDone()) {
                        lines.add("Exporting: " + name);
                    }
                });
                exportErrors.forEach((name, error) -> lines.add("Export of '" + name + "' failed: " + error));
                yield new CommandResult(true, String.join("\n", lines));
            }
            case "build" -> {
                requireLength(args, 4, "Usage: /architect blueprint build <name>");
                String name = args[3].toLowerCase(Locale.ROOT);
                CompletableFuture<Long> export = exports.get(name);
                if (export != null && !export.isDone()) {
                    throw new IllegalStateException("Blueprint '" + name + "' is still being exported. Try again shortly.");
                }
                StoredBlueprint stored = blueprints.open(name);
                JobProgress progress = submit(playerId, world, stored, snapToChunk(origin));
                yield new CommandResult(true, "Saved blueprint '" + name + "' queued as job #" + progress.jobId() + " ("
                    + stored.sectionCount() + " sections).");
            }
            default -> throw new IllegalArgumentException(usage);
        };
    }

    private CommandResult handleQueue() {
        List<JobProgress> jobs = queue.snapshot();
        if (jobs.isEmpty()) {
            return new CommandResult(true, "Build queue is empty.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Build queue (" + jobs.size() + "/" + queue.settings().maxQueuedJobs() + "):");
        jobs.forEach(job -> lines.add(job.describe()));
        return new CommandResult(true, String.join("\n", lines));
    }

    private CommandResult handleProgress(UUID playerId) {
        return queue.progress(playerId)
            .map(progress -> new CommandResult(true, progress.describe()))
            .orElseGet(() -> new CommandResult(false, "You have no builds yet."));
    }

    private CommandResult handlePause(UUID playerId, BlockWorld world) {
        return queue.pause(playerId, new BlockWorldAccess(world))
            .map(progress -> new CommandResult(true, "Paused " + progress.describe() + ". Use /architect resume to continue."))
            .orElseGet(() -> new CommandResult(false, "Nothing to pause."));
    }

    private CommandResult handleResume(UUID playerId) {
        return queue.resume(playerId)
            .map(progress -> new CommandResult(true, "Resumed " + progress.describe() + "."))
            .orElseGet(() -> new CommandResult(false, "Nothing to resume."));
    }

    private CommandResult handleCancel(UUID playerId, BlockWorld world) {
        Optional<JobProgress> cancelled = queue.cancel(playerId, new BlockWorldAccess(world));
        return cancelled
            .map(progress -> new CommandResult(true, "Cancelled " + progress.describe() + ". Use /architect undo to remove placed blocks."))
            .orElseGet(() -> new CommandResult(false, "Nothing to cancel."));
    }

    private CommandResult handleUndo(UUID playerId) {
        if (!queue.isBusy(playerId) && !queue.hasUndo(playerId)) {
            return new CommandResult(false, "No completed build to undo.");
        }
        JobProgress progress = queue.undoLast(playerId);
        return new CommandResult(true, "Undo queued for '" + progress.name() + "' (" + progress.unitsTotal()
            + " blocks). Use /architect progress.");
    }

    private CommandResult handleCamera(UUID playerId, String[] args) {
        requireLength(args, 3, "Usage: /architect camera <auto|orbit|follow|wide|stop|status|flyby|top-down|reveal> [seconds]");
        String action = args[2].toLowerCase(Locale.ROOT);
        if (cameraControl != null && isLiveCameraAction(action)) {
            if ("status".equals(action)) {
                return new CommandResult(true, cameraControl.describe(playerId));
            }
            return new CommandResult(true, cameraControl.setMode(playerId, CameraMode.fromId(action)));
        }
        if ("status".equals(action)) {
            return new CommandResult(false, "The cinematic camera is not available on this server.");
        }
        ShotType type = ShotType.fromId(args[2]);
        int seconds = args.length > 3 ? parseInt(args[3], "seconds") : 20;
        if (seconds < 1 || seconds > 600) {
            throw new IllegalArgumentException("seconds must be in range 1..600.");
        }
        CameraPath path = camera.suggest(playerId, type, seconds)
            .orElseThrow(() -> new IllegalStateException("No build to film yet. Start a build first."));
        var first = path.keyframes().get(0);
        return new CommandResult(true, String.format(Locale.ROOT,
            "Planned %s shot: %d keyframes over %ds starting at (%.0f, %.0f, %.0f). Playback needs a camera recorder (not included yet).",
            type.id(), path.keyframes().size(), seconds, first.x(), first.y(), first.z()));
    }

    /** Modes handled by the live client camera; the remaining ids keep planning an offline shot path. */
    private static boolean isLiveCameraAction(String action) {
        return switch (action) {
            case "auto", "orbit", "follow", "wide", "stop", "none", "status" -> true;
            default -> false;
        };
    }

    private CommandResult handleNpc(String[] args) {
        requireLength(args, 3, "Usage: /architect npc <on|off|status>");
        if (crewControl == null) {
            return new CommandResult(false, "Builder NPCs are not available on this server.");
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on", "enable", "true" -> new CommandResult(true, crewControl.setEnabled(true));
            case "off", "disable", "false" -> new CommandResult(true, crewControl.setEnabled(false));
            case "status" -> new CommandResult(true, crewControl.describe());
            default -> new CommandResult(false, "Usage: /architect npc <on|off|status>");
        };
    }

    private void startExport(String name, BlueprintSource blueprint) {
        CompletableFuture<Long> running = exports.get(name);
        if (running != null && !running.isDone()) {
            throw new IllegalStateException("Plan '" + name + "' is already being exported.");
        }
        exportErrors.remove(name);
        CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> blueprints.save(name, blueprint), exportExecutor);
        future.whenComplete((sections, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                exportErrors.put(name, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            }
        });
        exports.put(name, future);
    }

    private JobProgress submit(UUID playerId, BlockWorld world, BlueprintSource source, Vec3i origin) {
        BlockCatalog.requireSupported(source.palette().blockIds());
        return queue.submit(playerId, source, origin, new BlockWorldAccess(world));
    }

    private CommandResult queueBuild(UUID playerId, BlockWorld world, Vec3i origin, Blueprint blueprint, String successMessage, int maxBlocks, int maxAbsCoordinate) {
        validateSupportedBlocks(blueprint);
        BlueprintValidation.validateBlueprint(blueprint, maxBlocks, maxAbsCoordinate);
        submit(playerId, world, SectionedBlueprint.fromBlueprint(blueprint), origin);
        return new CommandResult(true, successMessage + " (" + blueprint.blocks().size() + " blocks)");
    }

    private String planIdArgument(UUID playerId, String[] args) {
        if (args.length > 3) {
            return args[3].toLowerCase(Locale.ROOT);
        }
        String loaded = loadedPlans.get(playerId);
        if (loaded == null) {
            throw new IllegalArgumentException("No plan loaded. Use /architect image load <planId> or pass a plan id.");
        }
        return loaded;
    }

    private static SceneRegion megaRegion(RegionType type, int size, int rotation, MirrorAxis mirror) {
        int diameter = 2 * size + 1;
        return switch (type) {
            case PALACE_CORE -> region(type, 0, diameter, clamp(2 * size, 16, 160), diameter, rotation, mirror);
            case BRIDGE -> region(type, 0, size, 10, 7, rotation, mirror);
            case TERRACE -> region(type, 0, diameter, 12, diameter, rotation, mirror);
            case ISLAND -> region(type, 0, diameter, clamp(size * 4 / 5, 4, 128), diameter, rotation, mirror);
            case WATERFALL -> region(type, 0, size, 40, 1, rotation, mirror);
            case PATH -> region(type, 0, size, 1, 3, rotation, mirror);
            case GARDEN -> region(type, 0, diameter, 2, diameter, rotation, mirror);
            case CHERRY_TREES -> region(type, 0, diameter, 10, diameter, rotation, mirror);
            case CLOUDS -> new SceneRegion("clouds", type, -size / 2, 0, -size / 2, size, 4, size, rotation, mirror, 7L);
        };
    }

    private static SceneRegion region(RegionType type, int y, int sizeX, int sizeY, int sizeZ, int rotation, MirrorAxis mirror) {
        return new SceneRegion(type.id(), type, 0, y, 0, sizeX, sizeY, sizeZ, rotation, mirror, 7L);
    }

    private static int defaultMegaSize(RegionType type) {
        return switch (type) {
            case PALACE_CORE -> 32;
            case BRIDGE -> 96;
            case TERRACE, ISLAND -> 40;
            case WATERFALL -> 9;
            case PATH -> 64;
            case GARDEN -> 24;
            case CHERRY_TREES -> 32;
            case CLOUDS -> 256;
        };
    }

    private static Vec3i snapToChunk(Vec3i origin) {
        return new Vec3i(Math.floorDiv(origin.x(), 16) * 16, origin.y(), Math.floorDiv(origin.z(), 16) * 16);
    }

    private static int worldMaxBlocks(int radius) {
        int side = radius * 2 + 1;
        return side * side + 4_096;
    }

    private static void validateSupportedBlocks(Blueprint blueprint) {
        String invalid = blueprint.blocks().stream()
            .map(b -> b.blockId())
            .map(BlueprintValidation::requireBlockId)
            .filter(block -> !BlockCatalog.isSupported(block))
            .findFirst()
            .orElse(null);
        if (invalid != null) {
            throw new IllegalArgumentException("Unsupported block for foundation: " + invalid);
        }
    }

    private CommandResult help() {
        return new CommandResult(true,
            String.join("\n", Arrays.asList(
                "/architect build <house|castle|temple|village>",
                "/architect shape wall <length> <height> <block>",
                "/architect shape sphere <radius> <block>",
                "/architect shape column <height> <block>",
                "/architect transform <upgrade|style|damage> <template>",
                "/architect world <kingdom>",
                "/architect mega <" + MEGA_TYPES + "> [size] [rotation] [mirror none|x|z]",
                "/architect image plan <uploads/<file>|placeholder:<name>> <planId> [scale 1.." + ArchitectConfig.MAX_IMAGE_SCALE + "]",
                "/architect image load <planId>",
                "/architect image preview [planId]",
                "/architect image build [planId]",
                "/architect image export [planId]",
                "/architect image list",
                "/architect blueprint list",
                "/architect blueprint build <name>",
                "/architect queue",
                "/architect progress",
                "/architect pause",
                "/architect resume",
                "/architect cancel",
                "/architect undo",
                "/architect camera <auto|orbit|follow|wide|stop|status>",
                "/architect camera <flyby|top-down|reveal> [seconds]",
                "/architect npc <on|off|status>",
                "/architect help"
            )));
    }

    private static void requireLength(String[] args, int minLength, String usage) {
        if (args.length < minLength) {
            throw new IllegalArgumentException(usage);
        }
    }

    private static int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be an integer.");
        }
    }

    private static int parseRotation(String value) {
        int rotation = parseInt(value, "rotation");
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
            throw new IllegalArgumentException("rotation must be 0, 90, 180 or 270.");
        }
        return rotation;
    }

    private static MirrorAxis parseMirror(String value) {
        try {
            return MirrorAxis.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("mirror must be none, x or z.");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Path defaultDataRoot() {
        String configured = System.getProperty("architect.dataDir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        try {
            return Files.createTempDirectory("architect-data-");
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create a temporary architect data directory", ex);
        }
    }

    public record TickSummary(long progressedBlocks, int completedSessions, int remainingJobs) {
    }
}
