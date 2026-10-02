package com.annaschneider.minecraft1.video;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Finds optional external programs: an explicit path, then an environment variable, then the {@code PATH}. */
public final class ExecutableLocator {
    private final String pathVariable;
    private final boolean windows;
    private final java.util.function.Function<String, String> environment;

    public ExecutableLocator() {
        this(System.getenv("PATH"), System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), System::getenv);
    }

    public ExecutableLocator(String pathVariable, boolean windows, java.util.function.Function<String, String> environment) {
        this.pathVariable = pathVariable == null ? "" : pathVariable;
        this.windows = windows;
        this.environment = environment;
    }

    /**
     * @param configured   path chosen by the user (file or folder containing the program), may be blank
     * @param envVariable  environment variable that may hold the path, e.g. {@code ARCHITECT_FFMPEG}
     * @param programName  program name without extension, e.g. {@code ffmpeg}
     * @param extraFolders additional folders to search (e.g. a bundled tools folder)
     */
    public Optional<Path> find(String configured, String envVariable, String programName, List<Path> extraFolders) {
        Optional<Path> chosen = check(configured, programName);
        if (chosen.isPresent() || (configured != null && !configured.isBlank())) {
            return chosen;
        }
        Optional<Path> fromEnv = envVariable == null ? Optional.empty() : check(environment.apply(envVariable), programName);
        if (fromEnv.isPresent()) {
            return fromEnv;
        }
        for (Path folder : extraFolders) {
            Optional<Path> found = inFolder(folder, programName);
            if (found.isPresent()) {
                return found;
            }
        }
        for (String entry : pathVariable.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Optional<Path> found = inFolder(Path.of(entry.strip()), programName);
                if (found.isPresent()) {
                    return found;
                }
            } catch (InvalidPathException ignored) {
                // skip malformed PATH entries
            }
        }
        return Optional.empty();
    }

    private Optional<Path> check(String value, String programName) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(value.strip());
            if (Files.isDirectory(path)) {
                return inFolder(path, programName);
            }
            return Files.isRegularFile(path) ? Optional.of(path.toAbsolutePath()) : Optional.empty();
        } catch (InvalidPathException ex) {
            return Optional.empty();
        }
    }

    private Optional<Path> inFolder(Path folder, String programName) {
        for (String name : windows ? List.of(programName + ".exe", programName) : List.of(programName)) {
            Path candidate = folder.resolve(name);
            if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
                return Optional.of(candidate.toAbsolutePath());
            }
        }
        return Optional.empty();
    }
}
