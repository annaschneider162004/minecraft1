package com.annaschneider.minecraft1.link;

import com.google.gson.annotations.SerializedName;

/** Kinds of messages sent by the mod. */
public enum MessageKind {
    /** Answer to the request with the same id. */
    @SerializedName("response") RESPONSE,
    /** Unsolicited job progress update for the connected player. */
    @SerializedName("progress") PROGRESS,
    /** Unsolicited informational message (e.g. "player left the game"). */
    @SerializedName("log") LOG
}
