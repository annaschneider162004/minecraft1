package com.annaschneider.minecraft1.mod.link;

import java.util.Optional;

/**
 * Looks up online players. The Fabric adapter implements it with {@code server.getPlayerManager()}; it is called on the
 * server thread only.
 */
@FunctionalInterface
public interface PlayerDirectory {
    /** @param name player name, or {@code null} for the first player online (the host in single-player) */
    Optional<PlayerContext> find(String name);
}
