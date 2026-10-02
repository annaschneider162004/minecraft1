package com.annaschneider.minecraft1.link;

import com.google.gson.annotations.SerializedName;

/** Requests the desktop app can send to the mod. */
public enum RequestType {
    /** First request of every connection: carries the link token and optionally the player name. */
    @SerializedName("hello") HELLO,
    /** Server info plus the player's current/last job. */
    @SerializedName("status") STATUS,
    /** Stores an image (base64) in the mod's uploads folder and returns its {@code uploads/...} source. */
    @SerializedName("upload_image") UPLOAD_IMAGE,
    /** Creates and saves a scene plan from an uploaded image or a text prompt. */
    @SerializedName("plan") PLAN,
    /** Summarises a saved plan or a template without building it. */
    @SerializedName("preview") PREVIEW,
    @SerializedName("build") BUILD,
    @SerializedName("pause") PAUSE,
    @SerializedName("resume") RESUME,
    @SerializedName("cancel") CANCEL,
    @SerializedName("undo") UNDO,
    @SerializedName("record_start") RECORD_START,
    @SerializedName("record_stop") RECORD_STOP,
    @SerializedName("record_status") RECORD_STATUS,
    /** Applies saved, bounded camera and builder preferences to the connected runtime. */
    @SerializedName("settings") SETTINGS,
    /** Switches the cinematic camera mode and/or toggles the visible builder NPCs. */
    @SerializedName("camera") CAMERA
}
