package com.annaschneider.minecraft1.link;

import java.util.List;

/**
 * Capabilities and state reported by the mod in answer to {@code hello} and {@code status}.
 *
 * @param player       name of the player the connection acts for, or {@code null} if nobody is in a world
 * @param templates    template ids accepted by {@code build}/{@code preview} with {@code mode=template}
 * @param maxScale     largest {@code scale} accepted by {@code plan}
 */
public record ServerInfo(
    String modVersion,
    int protocol,
    String player,
    List<String> templates,
    int maxScale,
    long maxImageBytes
) {
    public ServerInfo {
        templates = templates == null ? List.of() : List.copyOf(templates);
    }
}
