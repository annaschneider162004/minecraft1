package com.annaschneider.minecraft1.mod.command;

import com.annaschneider.minecraft1.aifoundation.LocalBoundedWorldCreator;
import com.annaschneider.minecraft1.aifoundation.WorldCreationRequest;
import com.annaschneider.minecraft1.buildassistant.ShapeBuilderService;
import com.annaschneider.minecraft1.buildtransformer.BuildTransformerService;
import com.annaschneider.minecraft1.buildtransformer.TransformOperation;
import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintValidation;
import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.instantbuilder.TemplateRegistry;
import com.annaschneider.minecraft1.mod.runtime.BlockCatalog;
import com.annaschneider.minecraft1.mod.runtime.BlockWorld;
import com.annaschneider.minecraft1.mod.runtime.BuildQueueManager;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public final class ArchitectCommandEngine {
    private final TemplateRegistry templates = new TemplateRegistry();
    private final ShapeBuilderService shapes = new ShapeBuilderService();
    private final BuildTransformerService transformer = new BuildTransformerService();
    private final LocalBoundedWorldCreator worldCreator = new LocalBoundedWorldCreator();
    private final BuildQueueManager queue = new BuildQueueManager();

    public CommandResult execute(UUID playerId, BlockWorld world, String rawCommand) {
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
                case "build" -> handleBuild(playerId, world, args);
                case "shape" -> handleShape(playerId, world, args);
                case "transform" -> handleTransform(playerId, world, args);
                case "world" -> handleWorld(playerId, world, args);
                case "undo" -> handleUndo(playerId, world);
                default -> new CommandResult(false, "Unknown subcommand. Use /architect help.");
            };
        } catch (IllegalArgumentException ex) {
            return new CommandResult(false, ex.getMessage());
        }
    }

    public BuildQueueManager.TickSummary tick(BlockWorld world) {
        return queue.tick(world);
    }

    private CommandResult handleBuild(UUID playerId, BlockWorld world, String[] args) {
        requireLength(args, 3, "Usage: /architect build <house|castle|temple|village>");
        Blueprint blueprint = templates.create(args[2].toLowerCase(Locale.ROOT)).orElseThrow(() ->
            new IllegalArgumentException("Unknown template. Use house, castle, temple, village."));
        return queueBuild(playerId, world, blueprint, "Template queued: " + args[2]);
    }

    private CommandResult handleShape(UUID playerId, BlockWorld world, String[] args) {
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
        return queueBuild(playerId, world, blueprint, "Shape queued: " + shape);
    }

    private CommandResult handleTransform(UUID playerId, BlockWorld world, String[] args) {
        requireLength(args, 4, "Usage: /architect transform <upgrade|style|damage> <template>");
        TransformOperation op = TransformOperation.fromId(args[2]).orElseThrow(() ->
            new IllegalArgumentException("Unknown transform mode. Use upgrade, style, damage."));
        String template = args[3].toLowerCase(Locale.ROOT);
        Blueprint source = templates.create(template).orElseThrow(() ->
            new IllegalArgumentException("Unknown template. Use house, castle, temple, village."));
        Blueprint transformed = transformer.transform(op, template, source);
        return queueBuild(playerId, world, transformed, "Transform queued: " + op.name().toLowerCase(Locale.ROOT) + " " + template);
    }

    private CommandResult handleWorld(UUID playerId, BlockWorld world, String[] args) {
        requireLength(args, 3, "Usage: /architect world <kingdom>");
        if (!"kingdom".equalsIgnoreCase(args[2])) {
            throw new IllegalArgumentException("Unsupported world preset. Use kingdom.");
        }
        Blueprint blueprint = worldCreator.createPlan(new WorldCreationRequest("fantasy kingdom", 64)).previewBlueprint();
        return queueBuild(playerId, world, blueprint, "World preset queued: kingdom");
    }

    private CommandResult handleUndo(UUID playerId, BlockWorld world) {
        if (!queue.undo(playerId, world)) {
            return new CommandResult(false, "No completed build to undo or build still in progress.");
        }
        return new CommandResult(true, "Undo complete for latest session.");
    }

    private CommandResult queueBuild(UUID playerId, BlockWorld world, Blueprint blueprint, String successMessage) {
        validateSupportedBlocks(blueprint);
        BlueprintValidation.validateBlueprint(blueprint, 10_000, 96);
        if (!queue.start(playerId, blueprint, new Vec3i(0, 1, 0))) {
            return new CommandResult(false, "Build already in progress for this player.");
        }
        queue.tick(world);
        return new CommandResult(true, successMessage + " (" + blueprint.blocks().size() + " blocks)");
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
                "/architect undo",
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
}
