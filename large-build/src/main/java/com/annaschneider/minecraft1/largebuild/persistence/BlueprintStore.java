package com.annaschneider.minecraft1.largebuild.persistence;

import com.annaschneider.minecraft1.largebuild.blueprint.BlueprintSource;
import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Directory of exported {@code .mcab} blueprints, addressed by validated names (same rules as plan ids).
 */
public final class BlueprintStore {
    private final Path directory;
    private final long maxSections;

    public BlueprintStore(Path directory, long maxSections) {
        this.directory = directory.toAbsolutePath().normalize();
        this.maxSections = maxSections;
    }

    public Path directory() {
        return directory;
    }

    public Path pathOf(String name) {
        return directory.resolve(requireName(name) + BlueprintCodec.EXTENSION);
    }

    public long save(String name, BlueprintSource source) {
        try {
            return BlueprintCodec.write(source, pathOf(name), maxSections);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save blueprint '" + name + "': " + ex.getMessage(), ex);
        }
    }

    public StoredBlueprint open(String name) {
        Path file = pathOf(name);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No saved blueprint '" + name + "'. Export one with /architect image export <planId>.");
        }
        return BlueprintCodec.open(file, maxSections);
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return names;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + BlueprintCodec.EXTENSION)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - BlueprintCodec.EXTENSION.length());
                if (ScenePlan.ID_PATTERN.matcher(name).matches()) {
                    names.add(name);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not list blueprints: " + ex.getMessage(), ex);
        }
        names.sort(null);
        return names;
    }

    private static String requireName(String name) {
        if (name == null || !ScenePlan.ID_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid blueprint name '" + name + "'. Use 1-64 characters: a-z, 0-9, '_' or '-'.");
        }
        return name;
    }
}
