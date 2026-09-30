package com.annaschneider.minecraft1.link;

/** Handles validated requests on the game's server thread (see {@link LinkServer#drain}). */
@FunctionalInterface
public interface LinkRequestHandler {
    /** Returns the response; its {@code id} is replaced with the request's id. */
    LinkMessage handle(LinkConnection connection, LinkRequest request);
}
