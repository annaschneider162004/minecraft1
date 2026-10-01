package com.annaschneider.minecraft1.link;

/** A malformed or invalid message. The message text is meant to be shown to the user. */
public final class LinkProtocolException extends RuntimeException {
    public LinkProtocolException(String message) {
        super(message);
    }
}
