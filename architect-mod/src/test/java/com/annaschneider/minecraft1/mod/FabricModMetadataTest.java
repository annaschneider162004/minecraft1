package com.annaschneider.minecraft1.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the processed {@code fabric.mod.json} that Fabric Loader reads from the mod jar. */
class FabricModMetadataTest {
    private static JsonObject metadata() throws Exception {
        try (InputStream in = FabricModMetadataTest.class.getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(in, "fabric.mod.json must be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void declaresModIdVersionAndDependenciesForMinecraft1201() throws Exception {
        JsonObject json = metadata();
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(ArchitectFabricMod.MOD_ID, json.get("id").getAsString());
        String version = json.get("version").getAsString();
        assertFalse(version.contains("${"), "the build must expand ${version}: " + version);
        JsonObject depends = json.getAsJsonObject("depends");
        assertEquals("1.20.1", depends.get("minecraft").getAsString());
        assertTrue(depends.has("fabricloader"));
        assertTrue(depends.has("fabric-api"), "Fabric API is required for commands, events and networking");
    }

    @Test
    void entrypointsPointToInitializersOfTheRightSide() throws Exception {
        JsonObject entrypoints = metadata().getAsJsonObject("entrypoints");
        String main = entrypoints.getAsJsonArray("main").get(0).getAsString();
        String client = entrypoints.getAsJsonArray("client").get(0).getAsString();
        assertEquals(ArchitectFabricMod.class.getName(), main);
        assertTrue(ModInitializer.class.isAssignableFrom(Class.forName(main)));
        assertTrue(ClientModInitializer.class.isAssignableFrom(Class.forName(client, false, getClass().getClassLoader())));
        assertFalse(main.contains(".client."), "the common entrypoint must not live in the client-only package");
    }

    /** A dedicated server has no client classes: only the client entrypoint package may reference them. */
    @Test
    void commonClassesNeverReferenceClientOnlyClasses() throws Exception {
        Path classes = Path.of(ArchitectFabricMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> offenders;
        try (Stream<Path> files = Files.walk(classes)) {
            offenders = files
                .filter(file -> file.toString().endsWith(".class"))
                .filter(file -> !classes.relativize(file).toString().replace('\\', '/')
                    .startsWith("com/annaschneider/minecraft1/mod/client/"))
                .filter(file -> {
                    try {
                        String constants = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                        return constants.contains("net/minecraft/client/")
                            || constants.contains("com/annaschneider/minecraft1/mod/client/");
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .map(file -> classes.relativize(file).toString())
                .toList();
        }
        assertTrue(offenders.isEmpty(), "client-only references in common classes: " + offenders);
    }
}
