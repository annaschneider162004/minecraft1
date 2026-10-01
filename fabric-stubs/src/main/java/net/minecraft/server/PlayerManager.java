package net.minecraft.server;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PlayerManager {
    private final List<ServerPlayerEntity> players = new ArrayList<>();

    public List<ServerPlayerEntity> getPlayerList() {
        return Collections.unmodifiableList(players);
    }

    public void addPlayer(ServerPlayerEntity player) {
        players.add(player);
    }

    public void removePlayer(ServerPlayerEntity player) {
        players.remove(player);
    }

    public ServerPlayerEntity getPlayer(String name) {
        for (ServerPlayerEntity player : players) {
            if (player.getEntityName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    public ServerPlayerEntity getPlayer(UUID uuid) {
        for (ServerPlayerEntity player : players) {
            if (player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }
}
