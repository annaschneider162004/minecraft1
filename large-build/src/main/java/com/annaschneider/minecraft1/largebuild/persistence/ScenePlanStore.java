package com.annaschneider.minecraft1.largebuild.persistence;

import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores {@link ScenePlan}s as pretty-printed JSON ({@code <dir>/<planId>.json}). Plan ids are validated so they can
 * never escape the directory. Files are written atomically.
 */
public final class ScenePlanStore {
    public static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path directory;

    public ScenePlanStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path directory() {
        return directory;
    }

    public Path pathOf(String planId) {
        return directory.resolve(ScenePlan.requireId(planId) + ".json");
    }

    public boolean exists(String planId) {
        return Files.isRegularFile(pathOf(planId));
    }

    public Path save(ScenePlan plan) {
        Path target = pathOf(plan.id());
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, plan.id() + "-", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    GSON.toJson(plan, writer);
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            return target;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save plan '" + plan.id() + "': " + ex.getMessage(), ex);
        }
    }

    public ScenePlan load(String planId) {
        Path file = pathOf(planId);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No saved plan '" + planId + "'. Create one with /architect image plan <image> " + planId + ".");
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Saved plan '" + planId + "' is too large.");
            }
            ScenePlan plan;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                plan = GSON.fromJson(reader, ScenePlan.class);
            } catch (JsonParseException ex) {
                throw new IllegalArgumentException("Saved plan '" + planId + "' is invalid: " + rootMessage(ex));
            } catch (RuntimeException ex) {
                // Gson reports record constructor validation failures as plain RuntimeExceptions
                throw new IllegalArgumentException("Saved plan '" + planId + "' is invalid: " + rootMessage(ex));
            }
            if (plan == null) {
                throw new IllegalArgumentException("Saved plan '" + planId + "' is empty.");
            }
            if (!plan.id().equals(planId)) {
                throw new IllegalArgumentException("Saved plan file '" + planId + ".json' contains plan '" + plan.id() + "'.");
            }
            return plan;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read plan '" + planId + "': " + ex.getMessage(), ex);
        }
    }

    public List<String> list() {
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                String id = name.substring(0, name.length() - ".json".length());
                if (ScenePlan.ID_PATTERN.matcher(id).matches()) {
                    ids.add(id);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not list plans: " + ex.getMessage(), ex);
        }
        ids.sort(null);
        return ids;
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
