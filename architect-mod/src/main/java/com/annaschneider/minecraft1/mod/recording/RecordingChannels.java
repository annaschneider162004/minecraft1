package com.annaschneider.minecraft1.mod.recording;

import net.minecraft.util.Identifier;

/**
 * Networking channels and actions for server-client recording coordination.
 */
public final class RecordingChannels {
    public static final Identifier RECORD_CMD_CHANNEL = new Identifier("architect", "record_cmd");
    public static final Identifier RECORD_STAT_CHANNEL = new Identifier("architect", "record_stat");

    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_STATUS = "STATUS";

    private RecordingChannels() {
    }
}
