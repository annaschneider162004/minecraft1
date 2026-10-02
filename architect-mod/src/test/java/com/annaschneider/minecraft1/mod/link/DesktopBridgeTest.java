package com.annaschneider.minecraft1.mod.link;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.engine.BuildSettings;
import com.annaschneider.minecraft1.link.BuildMode;
import com.annaschneider.minecraft1.link.CameraNpcSettings;
import com.annaschneider.minecraft1.largebuild.camera.CameraMode;
import com.annaschneider.minecraft1.link.LinkClient;
import com.annaschneider.minecraft1.link.LinkException;
import com.annaschneider.minecraft1.link.LinkInfo;
import com.annaschneider.minecraft1.link.LinkMessage;
import com.annaschneider.minecraft1.link.LinkProtocol;
import com.annaschneider.minecraft1.link.LinkRequest;
import com.annaschneider.minecraft1.link.MessageKind;
import com.annaschneider.minecraft1.link.RegionBox;
import com.annaschneider.minecraft1.link.RequestType;
import com.annaschneider.minecraft1.mod.command.ArchitectCommandEngine;
import com.annaschneider.minecraft1.mod.runtime.InMemoryBlockWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopBridgeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path dataRoot;
    private ArchitectCommandEngine engine;
    private DesktopBridge bridge;
    private LinkInfo info;
    private Thread serverThread;
    private final InMemoryBlockWorld world = new InMemoryBlockWorld();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean playerOnline = new AtomicBoolean(true);
    private final PlayerContext steve = new PlayerContext(UUID.randomUUID(), "Steve", world, new Vec3i(5, 64, -7));
    private final BlockingQueue<LinkMessage> events = new LinkedBlockingQueue<>();

    private final LinkClient.Listener listener = new LinkClient.Listener() {
        @Override
        public void onEvent(LinkMessage message) {
            events.add(message);
        }

        @Override
        public void onDisconnected(String reason) {
        }
    };

    @BeforeEach
    void setUp() throws IOException {
        BuildSettings settings = new BuildSettings(16_384, 256, 0, 16, 2, 4_000_000L, 262_144, dataRoot.resolve("journals"));
        engine = new ArchitectCommandEngine(dataRoot, settings);
        bridge = new DesktopBridge(engine, name -> playerOnline.get() && (name == null || name.equals("Steve"))
            ? Optional.of(steve) : Optional.empty(), "test");
        info = bridge.start(0);
        serverThread = new Thread(() -> {
            // simulated server thread: engine and bridge are only touched here
            while (running.get()) {
                engine.tick(world);
                bridge.tick();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }, "server-thread");
        serverThread.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        running.set(false);
        serverThread.join();
        bridge.close();
        engine.close();
    }

    private LinkClient connect(String player) throws LinkException {
        return LinkClient.connect(info, "test-desktop", player, listener, TIMEOUT);
    }

    @Test
    void appliesSettingsOnConnectionAndWhenPlayerJoinsLater() throws Exception {
        AtomicReference<CameraNpcSettings> camera = new AtomicReference<>();
        AtomicReference<CameraNpcSettings> crew = new AtomicReference<>();
        AtomicInteger deliveries = new AtomicInteger();
        engine.setCameraControl(new ArchitectCommandEngine.CameraControl() {
            public String setMode(UUID id, CameraMode mode) { return "off"; }
            public String describe(UUID id) { return "off"; }
            public void applySettings(UUID id, CameraNpcSettings value) { camera.set(value); deliveries.incrementAndGet(); }
        });
        engine.setCrewControl(new ArchitectCommandEngine.CrewControl() {
            public String setEnabled(boolean enabled) { return "ok"; }
            public String describe() { return "ok"; }
            public void applySettings(UUID id, CameraNpcSettings value) { crew.set(value); }
        });
        CameraNpcSettings desired = new CameraNpcSettings(true, true, 6, 24, 10);
        try (LinkClient client = connect("Steve")) {
            assertTrue(client.call(LinkRequest.settings(desired)).isOk());
            assertEquals(desired, camera.get());
            assertEquals(desired, crew.get());
            playerOnline.set(false);
            Thread.sleep(30);
            playerOnline.set(true);
            for (int i = 0; i < 100 && deliveries.get() < 2; i++) {
                Thread.sleep(10);
            }
            assertEquals(2, deliveries.get(), "settings reapply after a player reconnects");
        }
        camera.set(null);
        playerOnline.set(false);
        try (LinkClient client = connect("Steve")) {
            assertTrue(client.call(LinkRequest.settings(desired)).isOk());
            assertNull(camera.get());
            playerOnline.set(true);
            for (int i = 0; i < 100 && camera.get() == null; i++) {
                Thread.sleep(10);
            }
            assertEquals(desired, camera.get());
        }
    }

    @Test
    void writesLinkFileAndRemovesItOnClose() throws IOException {
        Path file = dataRoot.resolve(LinkProtocol.LINK_FILE_NAME);
        assertEquals(info, LinkInfo.read(file));
        assertTrue(info.port() > 0);
        assertThrows(IllegalStateException.class, () -> bridge.start(0));
        bridge.close();
        assertFalse(Files.exists(file));
    }

    @Test
    void templatePreviewBuildProgressAndUndo() throws Exception {
        try (LinkClient client = connect(null)) {
            assertEquals("Steve", client.serverInfo().player());
            assertTrue(client.serverInfo().templates().contains("house"));

            LinkMessage preview = client.call(LinkRequest.preview(BuildMode.TEMPLATE, "house"));
            assertTrue(preview.isOk(), preview.message());
            assertTrue(preview.plan().estimatedBlocks() > 0);
            assertFalse(preview.plan().regions().isEmpty());

            LinkMessage unknown = client.call(LinkRequest.preview(BuildMode.TEMPLATE, "spaceship"));
            assertFalse(unknown.isOk());
            assertTrue(unknown.message().contains("Unknown template"), unknown.message());

            LinkMessage build = client.call(LinkRequest.build(BuildMode.TEMPLATE, "house"));
            assertTrue(build.isOk(), build.message());
            assertNotNull(build.job());

            LinkMessage done = awaitProgress("completed");
            assertEquals(100.0, done.job().percent());
            assertEquals("build", done.job().kind());

            LinkMessage undo = client.call(LinkRequest.of(RequestType.UNDO));
            assertTrue(undo.isOk(), undo.message());
            awaitProgress("completed");
            LinkMessage again = client.call(LinkRequest.of(RequestType.UNDO));
            assertFalse(again.isOk());
            assertEquals("No completed build to undo.", again.message());
        }
    }

    @Test
    void uploadPlanPreviewAndPauseResumeCancel() throws Exception {
        try (LinkClient client = connect("Steve")) {
            String data = Base64.getEncoder().encodeToString(pngHeader(1600, 900));
            LinkMessage upload = client.call(LinkRequest.uploadImage("Sky Palace.PNG", data));
            assertTrue(upload.isOk(), upload.message());
            assertEquals("uploads/desktop/sky-palace.png", upload.source());
            assertTrue(Files.isRegularFile(dataRoot.resolve("uploads/desktop/sky-palace.png")));

            LinkMessage plan = client.call(LinkRequest.planFromSource("palace", upload.source(), 1));
            assertTrue(plan.isOk(), plan.message());
            assertEquals("palace", plan.plan().planId());
            assertTrue(plan.plan().regions().stream().map(RegionBox::type).anyMatch("palace"::equals));
            assertTrue(plan.plan().text().contains("sections"), plan.plan().text());

            LinkMessage preview = client.call(LinkRequest.preview(BuildMode.PLAN, "palace"));
            assertEquals(plan.plan(), preview.plan());

            LinkMessage build = client.call(LinkRequest.build(BuildMode.PLAN, "palace"));
            assertTrue(build.isOk(), build.message());
            LinkMessage paused = client.call(LinkRequest.of(RequestType.PAUSE));
            assertTrue(paused.isOk(), paused.message());
            assertEquals("paused", paused.job().state());
            LinkMessage resumed = client.call(LinkRequest.of(RequestType.RESUME));
            assertTrue(resumed.isOk(), resumed.message());
            assertTrue(resumed.job().isActive());
            LinkMessage cancelled = client.call(LinkRequest.of(RequestType.CANCEL));
            assertTrue(cancelled.isOk(), cancelled.message());
            assertEquals("cancelled", cancelled.job().state());
            assertFalse(client.call(LinkRequest.of(RequestType.CANCEL)).isOk());

            LinkMessage fake = client.call(LinkRequest.uploadImage("fake.png", Base64.getEncoder().encodeToString("hello".getBytes())));
            assertTrue(fake.isOk());
            LinkMessage badPlan = client.call(LinkRequest.planFromSource("fake", fake.source(), 1));
            assertFalse(badPlan.isOk());
            assertTrue(badPlan.message().contains("not a recognised"), badPlan.message());
        }
    }

    @Test
    void promptBecomesKeywordPlan() throws Exception {
        try (LinkClient client = connect(null)) {
            LinkMessage plan = client.call(LinkRequest.planFromPrompt("my-castle", "A white Castle with a waterfall!", 1));
            assertTrue(plan.isOk(), plan.message());
            assertTrue(plan.message().contains("placeholder:a-white-castle-with-a-waterfall"), plan.message());
            assertTrue(plan.plan().regions().stream().map(RegionBox::type).anyMatch("waterfall"::equals));
            LinkMessage missing = client.call(LinkRequest.preview(BuildMode.PLAN, "nope"));
            assertFalse(missing.isOk());
            assertTrue(missing.message().contains("No saved plan 'nope'"), missing.message());
        }
    }

    @Test
    void missingPlayerIsReportedButPlanningStillWorks() throws Exception {
        playerOnline.set(false);
        try (LinkClient client = connect(null)) {
            assertNull(client.serverInfo().player());
            LinkMessage build = client.call(LinkRequest.build(BuildMode.TEMPLATE, "house"));
            assertFalse(build.isOk());
            assertTrue(build.message().contains("No player is in a world"), build.message());
            assertTrue(client.call(LinkRequest.planFromPrompt("offline", "island", 1)).isOk());
        }
        try (LinkClient client = connect("Alex")) {
            LinkMessage status = client.call(LinkRequest.of(RequestType.STATUS));
            assertTrue(status.isOk());
            assertTrue(status.message().contains("Player 'Alex' is not online"), status.message());
        }
    }

    private LinkMessage awaitProgress(String state) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            LinkMessage event = events.poll(100, TimeUnit.MILLISECONDS);
            if (event != null && event.kind() == MessageKind.PROGRESS && state.equals(event.job().state())) {
                return event;
            }
        }
        throw new AssertionError("no progress event with state " + state);
    }

    private static byte[] pngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeInt(13);
        out.writeBytes("IHDR");
        out.writeInt(width);
        out.writeInt(height);
        out.write(new byte[] {8, 6, 0, 0, 0});
        out.writeInt(0);
        return bytes.toByteArray();
    }

    @Test
    void fallsBackToAFreePortWhenTheConfiguredOneIsBusy(@TempDir Path otherRoot) throws Exception {
        ArchitectCommandEngine second = new ArchitectCommandEngine(otherRoot,
            new BuildSettings(64, 8, 0, 16, 2, 4_000_000L, 262_144, otherRoot.resolve("journals")));
        DesktopBridge other = new DesktopBridge(second, name -> Optional.empty(), "test");
        try {
            LinkInfo started = other.start(info.port());
            assertTrue(started.port() > 0 && started.port() != info.port());
            assertEquals(started, LinkInfo.read(otherRoot.resolve(LinkProtocol.LINK_FILE_NAME)));
        } finally {
            other.close();
            second.close();
        }
    }
}
