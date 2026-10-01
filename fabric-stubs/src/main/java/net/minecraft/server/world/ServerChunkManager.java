package net.minecraft.server.world;

import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServerChunkManager {
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    private final Map<ChunkTicketType<?>, Map<ChunkPos, Integer>> tickets = new HashMap<>();

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return loadedChunks.contains(new ChunkPos(chunkX, chunkZ));
    }

    public void setChunkLoaded(int chunkX, int chunkZ, boolean loaded) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (loaded) {
            loadedChunks.add(pos);
        } else {
            loadedChunks.remove(pos);
        }
    }

    public <T> void addTicket(ChunkTicketType<T> type, ChunkPos pos, int radius, T argument) {
        tickets.computeIfAbsent(type, k -> new HashMap<>()).put(pos, radius);
        // Loading chunk simulation
        loadedChunks.add(pos);
    }

    public <T> void removeTicket(ChunkTicketType<T> type, ChunkPos pos, int radius, T argument) {
        Map<ChunkPos, Integer> typeTickets = tickets.get(type);
        if (typeTickets != null) {
            typeTickets.remove(pos);
        }
    }

    public boolean hasTicket(ChunkTicketType<?> type, ChunkPos pos) {
        Map<ChunkPos, Integer> typeTickets = tickets.get(type);
        return typeTickets != null && typeTickets.containsKey(pos);
    }

    public int getTicketCount(ChunkTicketType<?> type) {
        Map<ChunkPos, Integer> typeTickets = tickets.get(type);
        return typeTickets == null ? 0 : typeTickets.size();
    }
}
