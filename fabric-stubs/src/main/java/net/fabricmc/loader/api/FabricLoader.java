package net.fabricmc.loader.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModVersion;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FabricLoader {
    private static FabricLoader instance = new FabricLoader();

    private final Map<String, ModContainer> mods = new HashMap<>();
    private EnvType envType = EnvType.SERVER;
    private Path configDir = Path.of("config");
    private Path gameDir = Path.of(".");

    public static FabricLoader getInstance() {
        return instance;
    }

    public static void setInstance(FabricLoader custom) {
        instance = custom;
    }

    public boolean isModLoaded(String modId) {
        return mods.containsKey(modId);
    }

    public Optional<ModContainer> getModContainer(String modId) {
        return Optional.ofNullable(mods.get(modId));
    }

    public EnvType getEnvironmentType() {
        return envType;
    }

    public void setEnvironmentType(EnvType envType) {
        this.envType = envType;
    }

    public Path getConfigDir() {
        return configDir;
    }

    public void setConfigDir(Path configDir) {
        this.configDir = configDir;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public void setGameDir(Path gameDir) {
        this.gameDir = gameDir;
    }

    public void registerMod(String modId, String version) {
        mods.put(modId, new ModContainer() {
            @Override
            public ModMetadata getMetadata() {
                return new ModMetadata() {
                    @Override
                    public String getId() {
                        return modId;
                    }

                    @Override
                    public String getName() {
                        return modId;
                    }

                    @Override
                    public ModVersion getVersion() {
                        return () -> version;
                    }

                    @Override
                    public String getDescription() {
                        return "";
                    }
                };
            }
        });
    }
}
