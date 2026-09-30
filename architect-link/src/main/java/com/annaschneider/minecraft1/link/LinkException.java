package com.annaschneider.minecraft1.link;

/** Checked, user-facing connection or request error on the desktop side. */
public final class LinkException extends Exception {
    public LinkException(String message) {
        super(message);
    }
}
