package com.annaschneider.minecraft1.link;

import com.google.gson.annotations.SerializedName;

/** What a {@code preview} / {@code build} request refers to. */
public enum BuildMode {
    /** A built-in template ({@link LinkRequest#template()}). */
    @SerializedName("template") TEMPLATE,
    /** A saved scene plan ({@link LinkRequest#planId()}), created from an image or a prompt. */
    @SerializedName("plan") PLAN
}
