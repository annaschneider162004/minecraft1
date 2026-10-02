package com.annaschneider.minecraft1.mod.runtime;

import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.largebuild.camera.CameraSettings;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.largebuild.npc.NpcSettings;
import com.annaschneider.minecraft1.link.LinkProtocol;

import java.nio.file.Path;

/**
 * Server-side limits, overridable with {@code -Darchitect.<name>=<value>} JVM properties.
 */
public final class ArchitectConfig {
    public static final int BLOCKS_PER_TICK = clamp(Integer.getInteger("architect.blocksPerTick", 64), 1, 16_384);
    public static final int SECTIONS_PER_TICK = clamp(Integer.getInteger("architect.sectionsPerTick", 8), 1, 256);
    public static final int TICK_BUDGET_MILLIS = clamp(Integer.getInteger("architect.tickBudgetMillis", 20), 0, 45);
    public static final int MAX_QUEUED_JOBS = clamp(Integer.getInteger("architect.maxQueuedJobs", 16), 1, 256);
    public static final int MAX_CONCURRENT_JOBS = clamp(Integer.getInteger("architect.maxConcurrentJobs", 2), 1, 16);
    public static final int MAX_EXPORT_SECTIONS = clamp(Integer.getInteger("architect.maxExportSections", 1_000_000), 1, 4_000_000);
    public static final int MAX_IMAGE_SCALE = clamp(Integer.getInteger("architect.maxImageScale", 16), 1, 24);
    /** Whether the desktop app link (127.0.0.1 only) is started. */
    public static final boolean LINK_ENABLED = Boolean.parseBoolean(System.getProperty("architect.link", "true"));
    public static final int LINK_PORT = clamp(Integer.getInteger("architect.linkPort", LinkProtocol.DEFAULT_PORT), 1024, 65_535);

    /** Whether players may switch the cinematic camera on at all. */
    public static final boolean CAMERA_ENABLED = Boolean.parseBoolean(System.getProperty("architect.camera", "true"));
    /** Camera mode a player starts with: off, auto, orbit, follow or wide. */
    public static final String CAMERA_DEFAULT_MODE = System.getProperty("architect.cameraMode", "off");
    /** Server ticks between two camera state packets (coalesced, never one per block). */
    public static final int CAMERA_UPDATE_TICKS = clamp(Integer.getInteger("architect.cameraUpdateTicks", 10), 2, 200);
    public static final int CAMERA_ORBIT_DISTANCE = clamp(Integer.getInteger("architect.cameraOrbitDistance", 18), 3, 128);
    public static final int CAMERA_ORBIT_HEIGHT = clamp(Integer.getInteger("architect.cameraOrbitHeight", 12), 1, 96);
    public static final int CAMERA_ORBIT_SPEED = clamp(Integer.getInteger("architect.cameraOrbitSpeed", 9), 1, 90);
    public static final int CAMERA_AUTO_SHOT_SECONDS = clamp(Integer.getInteger("architect.cameraAutoShotSeconds", 12), 3, 120);
    public static final int CAMERA_MAX_SPEED = clamp(Integer.getInteger("architect.cameraMaxSpeed", 18), 1, 64);
    public static final int CAMERA_MAX_DISTANCE = clamp(Integer.getInteger("architect.cameraMaxDistance", 192), 16, 512);

    /** Whether visible builder NPCs are spawned around active sections. */
    public static final boolean NPC_ENABLED = Boolean.parseBoolean(System.getProperty("architect.npcBuilders", "false"));
    public static final int NPC_MAX_WORKERS = clamp(Integer.getInteger("architect.npcMaxWorkers", 4), 1, 12);
    public static final int NPC_SECTIONS_PER_WORKER = clamp(Integer.getInteger("architect.npcSectionsPerWorker", 64), 16, 100_000);
    public static final int NPC_UPDATE_SECTIONS = clamp(Integer.getInteger("architect.npcUpdateSections", 2), 1, 64);

    private ArchitectConfig() {
    }

    public static CameraSettings cameraSettings() {
        return new CameraSettings(CAMERA_ORBIT_DISTANCE, CAMERA_ORBIT_HEIGHT, CAMERA_ORBIT_SPEED,
            CAMERA_AUTO_SHOT_SECONDS, CAMERA_MAX_SPEED, CAMERA_MAX_DISTANCE);
    }

    public static NpcSettings npcSettings() {
        return new NpcSettings(NPC_ENABLED, NPC_MAX_WORKERS, NPC_SECTIONS_PER_WORKER, NPC_UPDATE_SECTIONS);
    }

    /** Default camera mode; an invalid {@code -Darchitect.cameraMode} falls back to off instead of failing. */
    public static CameraMode defaultCameraMode() {
        if (!CAMERA_ENABLED) {
            return CameraMode.OFF;
        }
        try {
            return CameraMode.fromId(CAMERA_DEFAULT_MODE);
        } catch (IllegalArgumentException ex) {
            return CameraMode.OFF;
        }
    }

    public static BuildSettings buildSettings(Path journalDirectory) {
        BuildSettings defaults = BuildSettings.defaults();
        return new BuildSettings(BLOCKS_PER_TICK, SECTIONS_PER_TICK, TICK_BUDGET_MILLIS, MAX_QUEUED_JOBS, MAX_CONCURRENT_JOBS,
            defaults.maxSectionsPerJob(), defaults.journalMemoryEntries(), journalDirectory);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
