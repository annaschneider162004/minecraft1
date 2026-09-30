package com.annaschneider.minecraft1.largebuild.persistence;

import com.annaschneider.minecraft1.largebuild.blueprint.BlockPalette;
import com.annaschneider.minecraft1.largebuild.blueprint.BlockSection;
import com.annaschneider.minecraft1.largebuild.blueprint.BlueprintSource;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionCursor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compact streaming blueprint format ({@code .mcab}).
 *
 * <pre>
 * header (plain):  int magic "MCAB", int version, UTF name, 6 x int bounds, int paletteCount, UTF blockId...,
 *                  long sectionCount
 * body (gzip):     per non-empty section: int chunkX, int sectionY, int chunkZ,
 *                  then run-length pairs (varint runLength, short paletteIndex) covering 4096 cells (y,z,x order)
 * </pre>
 *
 * Writing streams from any {@link BlueprintSource} and never holds more than one section in memory; reading returns a
 * {@link StoredBlueprint} that decodes lazily while building.
 */
public final class BlueprintCodec {
    public static final int MAGIC = 0x4D434142;
    public static final int VERSION = 1;
    public static final String EXTENSION = ".mcab";

    private BlueprintCodec() {
    }

    /**
     * Writes {@code source} to {@code target} atomically. Returns the number of non-empty sections written.
     *
     * @throws IllegalArgumentException if the source has more than {@code maxSections} sections
     */
    public static long write(BlueprintSource source, Path target, long maxSections) throws IOException {
        if (source.sectionCount() > maxSections) {
            throw new IllegalArgumentException("Blueprint '" + source.name() + "' has " + source.sectionCount()
                + " sections; export is limited to " + maxSections + ".");
        }
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path body = Files.createTempFile(parent, "body-", ".tmp");
        Path assembled = Files.createTempFile(parent, "blueprint-", ".tmp");
        try {
            long written = 0;
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(body), 64 * 1024)));
                 SectionCursor cursor = source.openCursor()) {
                BlockSection section = new BlockSection();
                while (cursor.next(section)) {
                    if (section.isEmpty()) {
                        continue;
                    }
                    writeSection(out, section);
                    written++;
                }
            }
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(assembled)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeUTF(source.name());
                Bounds b = source.bounds();
                out.writeInt(b.minX());
                out.writeInt(b.minY());
                out.writeInt(b.minZ());
                out.writeInt(b.maxX());
                out.writeInt(b.maxY());
                out.writeInt(b.maxZ());
                BlockPalette palette = source.palette();
                var ids = palette.blockIds();
                out.writeInt(ids.size());
                for (String id : ids) {
                    out.writeUTF(id);
                }
                out.writeLong(written);
                Files.copy(body, out);
            }
            Files.move(assembled, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return written;
        } finally {
            Files.deleteIfExists(body);
            Files.deleteIfExists(assembled);
        }
    }

    /** Reads and validates the header; sections are decoded lazily by the returned blueprint's cursor. */
    public static StoredBlueprint open(Path file, long maxSections) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Saved blueprint not found: " + file.getFileName());
        }
        try (CountingInputStream counting = new CountingInputStream(new BufferedInputStream(Files.newInputStream(file)));
             DataInputStream in = new DataInputStream(counting)) {
            if (in.readInt() != MAGIC) {
                throw corrupt(file, "not an .mcab blueprint file");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw corrupt(file, "unsupported format version " + version);
            }
            String name = in.readUTF();
            if (name.isBlank()) {
                throw corrupt(file, "missing name");
            }
            Bounds bounds;
            try {
                bounds = new Bounds(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
            } catch (IllegalArgumentException ex) {
                throw corrupt(file, "invalid bounds");
            }
            int paletteCount = in.readInt();
            if (paletteCount < 0 || paletteCount >= BlockPalette.MAX_ENTRIES) {
                throw corrupt(file, "invalid palette size " + paletteCount);
            }
            BlockPalette palette = new BlockPalette();
            for (int i = 0; i < paletteCount; i++) {
                String id = in.readUTF();
                if (palette.indexOf(id) != i + 1) {
                    throw corrupt(file, "duplicate palette entry " + id);
                }
            }
            long sections = in.readLong();
            if (sections < 0 || sections > maxSections) {
                throw corrupt(file, "section count " + sections + " is outside 0.." + maxSections);
            }
            return new StoredBlueprint(file, name, palette, bounds, sections, counting.count());
        } catch (EOFException ex) {
            throw corrupt(file, "file is truncated");
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read blueprint " + file.getFileName() + ": " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Saved blueprint")) {
                throw ex;
            }
            throw corrupt(file, ex.getMessage());
        }
    }

    static IllegalArgumentException corrupt(Path file, String reason) {
        return new IllegalArgumentException("Saved blueprint " + file.getFileName() + " is corrupt: " + reason + ".");
    }

    private static void writeSection(DataOutputStream out, BlockSection section) throws IOException {
        out.writeInt(section.key().chunkX());
        out.writeInt(section.key().sectionY());
        out.writeInt(section.key().chunkZ());
        int i = 0;
        while (i < com.annaschneider.minecraft1.largebuild.blueprint.SectionKey.VOLUME) {
            short value = section.get(i);
            int run = 1;
            while (i + run < com.annaschneider.minecraft1.largebuild.blueprint.SectionKey.VOLUME && section.get(i + run) == value) {
                run++;
            }
            writeVarInt(out, run);
            out.writeShort(value);
            i += run;
        }
    }

    static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("varint too long");
    }

    static InputStream openBody(Path file, long offset) throws IOException {
        InputStream raw = Files.newInputStream(file);
        try {
            raw.skipNBytes(offset);
            return new GZIPInputStream(new BufferedInputStream(raw, 64 * 1024), 64 * 1024);
        } catch (IOException | RuntimeException ex) {
            raw.close();
            throw ex;
        }
    }

    private static final class CountingInputStream extends java.io.FilterInputStream {
        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }

        long count() {
            return count;
        }
    }
}
