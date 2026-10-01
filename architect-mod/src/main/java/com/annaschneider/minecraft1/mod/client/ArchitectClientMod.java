package com.annaschneider.minecraft1.mod.client;

import com.annaschneider.minecraft1.link.LinkCodec;
import com.annaschneider.minecraft1.link.RecordingStatus;
import com.annaschneider.minecraft1.mod.camera.CameraChannels;
import com.annaschneider.minecraft1.mod.client.camera.ClientCinematicCamera;
import com.annaschneider.minecraft1.mod.client.recording.ClientRecordingManager;
import com.annaschneider.minecraft1.mod.recording.RecordingChannels;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.logging.Logger;

/**
 * Client-side Fabric entrypoint for Minecraft Architect.
 * Exclusively loaded in client environments, preventing dedicated servers from referencing client classes.
 */
@Environment(EnvType.CLIENT)
public final class ArchitectClientMod implements ClientModInitializer {
    private static final Logger LOGGER = Logger.getLogger("com.annaschneider.minecraft1.mod.client");
    private final ClientRecordingManager recordingManager = new ClientRecordingManager();
    private final ClientCinematicCamera cinematicCamera = new ClientCinematicCamera();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Architect] Initializing client recording integration...");
        ClientPlayNetworking.registerGlobalReceiver(RecordingChannels.RECORD_CMD_CHANNEL, (client, handler, buf, responseSender) -> {
            String command = buf.readString();
            client.execute(() -> {
                RecordingStatus status = recordingManager.handleCommand(command);
                if (RecordingChannels.ACTION_STOP.equals(command)) {
                    // stopping the recording always returns the player to their own view
                    cinematicCamera.stop(client);
                }
                String json = LinkCodec.encode(status);
                PacketByteBuf reply = PacketByteBufs.create();
                reply.writeString(json);
                responseSender.sendPacket(RecordingChannels.RECORD_STAT_CHANNEL, reply);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CameraChannels.CAMERA_MODE_CHANNEL, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> cinematicCamera.onMode(client, json));
        });
        ClientPlayNetworking.registerGlobalReceiver(CameraChannels.CAMERA_STATE_CHANNEL, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> cinematicCamera.onState(client, json));
        });

        ClientTickEvents.END_CLIENT_TICK.register(cinematicCamera::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> cinematicCamera.onDisconnect(client));
    }

    public ClientCinematicCamera cinematicCamera() {
        return cinematicCamera;
    }

    public ClientRecordingManager recordingManager() {
        return recordingManager;
    }
}
