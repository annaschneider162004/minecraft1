package com.annaschneider.minecraft1.mod.fabric;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.mod.link.PlayerContext;
import com.annaschneider.minecraft1.mod.link.PlayerDirectory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric implementation of {@link PlayerDirectory} backed by {@link MinecraftServer#getPlayerManager()}.
 * Dynamically resolves player world and position to ensure fresh data at execution time.
 */
public final class FabricPlayerDirectory implements PlayerDirectory {
    private final MinecraftServer server;
    private final Map<ServerWorld, FabricBlockWorld> worlds = new ConcurrentHashMap<>();

    public FabricPlayerDirectory(MinecraftServer server) {
        this.server = server;
    }

    public FabricBlockWorld worldAdapter(ServerWorld world) {
        return worlds.computeIfAbsent(world, FabricBlockWorld::new);
    }

    public void releaseAllChunkTickets() {
        for (FabricBlockWorld world : worlds.values()) {
            world.releaseAllChunks();
        }
        worlds.clear();
    }

    @Override
    public Optional<PlayerContext> find(String name) {
        if (server == null || server.getPlayerManager() == null) {
            return Optional.empty();
        }
        ServerPlayerEntity player;
        if (name != null && !name.isBlank()) {
            player = server.getPlayerManager().getPlayer(name);
            if (player == null) {
                return Optional.empty();
            }
        } else {
            List<ServerPlayerEntity> playerList = server.getPlayerManager().getPlayerList();
            if (playerList == null || playerList.isEmpty()) {
                return Optional.empty();
            }
            if (playerList.size() == 1) {
                player = playerList.get(0);
            } else {
                player = playerList.stream()
                    .min(Comparator.comparing(p -> p.getName().getString()))
                    .orElse(playerList.get(0));
            }
        }

        ServerWorld serverWorld = player.getServerWorld();
        FabricBlockWorld blockWorld = worldAdapter(serverWorld);
        Vec3i position = new Vec3i(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        return Optional.of(new PlayerContext(player.getUuid(), player.getName().getString(), blockWorld, position));
    }
}
