package com.annaschneider.minecraft1.largebuild.persistence;

import com.annaschneider.minecraft1.largebuild.blueprint.BlockPalette;
import com.annaschneider.minecraft1.largebuild.blueprint.BlockSection;
import com.annaschneider.minecraft1.largebuild.blueprint.BlueprintSource;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionCursor;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionKey;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * A blueprint backed by an {@code .mcab} file. Each cursor streams sections from disk; nothing but the palette and
 * header is kept in memory. Corrupt data surfaces as {@link IllegalStateException} (the build job then fails cleanly
 * and whatever was placed stays undoable).
 */
public final class StoredBlueprint implements BlueprintSource {
    private final Path file;
    private final String name;
    private final BlockPalette palette;
    private final Bounds bounds;
    private final long sectionCount;
    private final long bodyOffset;

    StoredBlueprint(Path file, String name, BlockPalette palette, Bounds bounds, long sectionCount, long bodyOffset) {
        this.file = file;
        this.name = name;
        this.palette = palette;
        this.bounds = bounds;
        this.sectionCount = sectionCount;
        this.bodyOffset = bodyOffset;
    }

    public Path file() {
        return file;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public BlockPalette palette() {
        return palette;
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public long sectionCount() {
        return sectionCount;
    }

    @Override
    public SectionCursor openCursor() {
        DataInputStream in;
        try {
            in = new DataInputStream(BlueprintCodec.openBody(file, bodyOffset));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not open blueprint " + file.getFileName() + ": " + ex.getMessage(), ex);
        }
        int paletteSize = palette.size();
        return new SectionCursor() {
            private long remaining = sectionCount;
            private long previousKey = Long.MIN_VALUE;
            private final short[] buffer = new short[SectionKey.VOLUME];

            @Override
            public boolean next(BlockSection target) {
                if (remaining <= 0) {
                    return false;
                }
                try {
                    SectionKey key = new SectionKey(in.readInt(), in.readInt(), in.readInt());
                    if (!key.bounds().intersects(bounds)) {
                        throw corrupt("section " + key + " lies outside the blueprint bounds");
                    }
                    long packed = key.pack();
                    if (packed <= previousKey) {
                        throw corrupt("sections are not in ascending order");
                    }
                    previousKey = packed;
                    int filled = 0;
                    while (filled < SectionKey.VOLUME) {
                        int run = BlueprintCodec.readVarInt(in);
                        short value = in.readShort();
                        if (run <= 0 || run > SectionKey.VOLUME - filled) {
                            throw corrupt("invalid run length");
                        }
                        if (value < 0 || value >= paletteSize) {
                            throw corrupt("palette index " + value + " out of range");
                        }
                        java.util.Arrays.fill(buffer, filled, filled + run, value);
                        filled += run;
                    }
                    target.copyFrom(key, buffer);
                    remaining--;
                    return true;
                } catch (EOFException ex) {
                    throw corrupt("file is truncated");
                } catch (IOException ex) {
                    throw new IllegalStateException("Could not read blueprint " + file.getFileName() + ": " + ex.getMessage(), ex);
                } catch (IllegalArgumentException ex) {
                    throw corrupt(ex.getMessage());
                }
            }

            @Override
            public void close() {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing useful to do on close
                }
            }
        };
    }

    private IllegalStateException corrupt(String reason) {
        return new IllegalStateException(BlueprintCodec.corrupt(file, reason).getMessage());
    }
}
