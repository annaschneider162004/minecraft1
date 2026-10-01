package com.annaschneider.minecraft1.link;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Contents of {@code desktop-link.json}, written by the mod into its data folder (e.g.
 * {@code %APPDATA%\.minecraft\config\architect\desktop-link.json}) while it listens, and read by the desktop app to
 * find the port and token without any manual setup.
 */
public record LinkInfo(int protocol, String host, int port, String token, String modVersion) {
    public static LinkInfo create(int port, String modVersion) {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return new LinkInfo(LinkProtocol.VERSION, "127.0.0.1", port, HexFormat.of().formatHex(bytes), modVersion);
    }

    public LinkInfo withPort(int newPort) {
        return new LinkInfo(protocol, host, newPort, token, modVersion);
    }

    public static LinkInfo read(Path file) throws IOException {
        if (Files.size(file) > 64 * 1024) {
            throw new IOException(file + " is too large to be a link file.");
        }
        LinkInfo info;
        try {
            info = LinkCodec.decode(Files.readString(file), LinkInfo.class);
        } catch (LinkProtocolException ex) {
            throw new IOException(file + " is not a valid link file.");
        }
        if (info.port() < 1 || info.port() > 65_535 || info.token() == null || info.token().isBlank()) {
            throw new IOException(file + " is incomplete (port/token missing).");
        }
        return info;
    }

    /** Writes the file atomically and, where supported, readable by the current user only. */
    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, LinkCodec.encode(this));
        try {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ex) {
            // Windows: the file lives in the user's profile, which is already private to that user.
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
