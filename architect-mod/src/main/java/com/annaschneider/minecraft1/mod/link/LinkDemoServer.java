package com.annaschneider.minecraft1.mod.link;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.link.LinkInfo;
import com.annaschneider.minecraft1.mod.runtime.ArchitectServerRuntime;
import com.annaschneider.minecraft1.mod.runtime.InMemoryBlockWorld;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Headless stand-in for a Minecraft server: one simulated player in an in-memory world, ticking at 20 TPS, with the
 * desktop link enabled. Lets you try the desktop app without starting the game:
 * {@code gradle :architect-mod:runLinkDemo}, then {@code gradle :architect-desktop:runWithDemo}.
 */
public final class LinkDemoServer {
    private LinkDemoServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        Path dataRoot = Path.of(args.length > 0 ? args[0] : System.getProperty("architect.dataDir", "run/link-demo"));
        InMemoryBlockWorld world = new InMemoryBlockWorld();
        PlayerContext player = new PlayerContext(UUID.nameUUIDFromBytes("DemoPlayer".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "DemoPlayer", world,
            new Vec3i(0, 64, 0));
        PlayerDirectory players = name -> name == null || name.equalsIgnoreCase(player.name()) ? Optional.of(player) : Optional.empty();
        try (ArchitectServerRuntime runtime = new ArchitectServerRuntime(dataRoot, players, "demo")) {
            Optional<LinkInfo> info = runtime.startLink();
            if (info.isEmpty()) {
                System.err.println(runtime.linkError().orElse("Desktop link not started."));
                return;
            }
            System.out.println("Architect link demo listening on 127.0.0.1:" + info.get().port() + " as player "
                + player.name() + ".");
            System.out.println("Link file: " + runtime.bridge().linkFile().orElseThrow().toAbsolutePath());
            System.out.println("Press Ctrl+C to stop.");
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
            long next = System.nanoTime();
            while (!Thread.currentThread().isInterrupted()) {
                runtime.tick(world);
                next += 50_000_000L;
                long sleep = next - System.nanoTime();
                if (sleep > 0) {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                } else {
                    next = System.nanoTime();
                }
            }
        }
    }
}
