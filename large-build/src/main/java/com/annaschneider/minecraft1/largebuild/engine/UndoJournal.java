package com.annaschneider.minecraft1.largebuild.engine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Append-only record of the block states replaced by a build. Entries stay in compact primitive arrays until
 * {@code memoryLimit} is reached, then everything is spilled to a gzip file so multi-million block builds keep a flat
 * heap footprint. Each position is recorded at most once per build, so replaying in order restores the world.
 */
public final class UndoJournal {
    private static final byte TAG_STATE = 1;
    private static final byte TAG_ENTRY = 2;

    private final Path spillDirectory;
    private final int memoryLimit;
    private final List<String> states = new ArrayList<>();
    private final Map<String, Integer> stateIndex = new HashMap<>();
    private int[] coords = new int[3 * 256];
    private int[] stateRefs = new int[256];
    private int memoryCount;
    private long size;
    private Path file;
    private DataOutputStream out;
    private int writtenStates;
    private boolean sealed;

    public UndoJournal(Path spillDirectory, int memoryLimit) {
        this.spillDirectory = spillDirectory;
        this.memoryLimit = Math.max(1, memoryLimit);
    }

    public static UndoJournal inMemory() {
        return new UndoJournal(null, Integer.MAX_VALUE);
    }

    public void record(int x, int y, int z, String previous) {
        if (sealed) {
            throw new IllegalStateException("Journal is sealed.");
        }
        int ref = stateIndex.computeIfAbsent(previous, s -> {
            states.add(s);
            return states.size() - 1;
        });
        size++;
        if (out != null) {
            writeEntry(x, y, z, ref);
            return;
        }
        if (memoryCount == stateRefs.length) {
            stateRefs = Arrays.copyOf(stateRefs, stateRefs.length * 2);
            coords = Arrays.copyOf(coords, coords.length * 2);
        }
        coords[memoryCount * 3] = x;
        coords[memoryCount * 3 + 1] = y;
        coords[memoryCount * 3 + 2] = z;
        stateRefs[memoryCount++] = ref;
        if (spillDirectory != null && memoryCount >= memoryLimit) {
            spill();
        }
    }

    public long size() {
        return size;
    }

    public boolean isSpilled() {
        return file != null;
    }

    public Path file() {
        return file;
    }

    public void seal() {
        if (sealed) {
            return;
        }
        sealed = true;
        if (out != null) {
            try {
                out.close();
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to finish undo journal.", ex);
            } finally {
                out = null;
            }
        }
    }

    public void discard() {
        seal();
        coords = new int[0];
        stateRefs = new int[0];
        memoryCount = 0;
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // best effort cleanup
            }
        }
    }

    public Reader openReader() {
        seal();
        if (file == null) {
            return new MemoryReader();
        }
        try {
            return new FileReader(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to open undo journal.", ex);
        }
    }

    private void spill() {
        try {
            Files.createDirectories(spillDirectory);
            file = spillDirectory.resolve("undo-" + UUID.randomUUID() + ".journal.gz");
            out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file), 1 << 16), 1 << 16));
            for (int i = 0; i < memoryCount; i++) {
                writeEntry(coords[i * 3], coords[i * 3 + 1], coords[i * 3 + 2], stateRefs[i]);
            }
            memoryCount = 0;
            coords = new int[0];
            stateRefs = new int[0];
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to spill undo journal to disk.", ex);
        }
    }

    private void writeEntry(int x, int y, int z, int ref) {
        try {
            while (writtenStates <= ref) {
                out.writeByte(TAG_STATE);
                out.writeUTF(states.get(writtenStates++));
            }
            out.writeByte(TAG_ENTRY);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.writeInt(ref);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write undo journal.", ex);
        }
    }

    /** Mutable entry reused by readers. */
    public static final class Entry {
        public int x;
        public int y;
        public int z;
        public String previous;
    }

    public interface Reader extends AutoCloseable {
        boolean next(Entry entry);

        @Override
        void close();
    }

    private final class MemoryReader implements Reader {
        private int index;

        @Override
        public boolean next(Entry entry) {
            if (index >= memoryCount) {
                return false;
            }
            entry.x = coords[index * 3];
            entry.y = coords[index * 3 + 1];
            entry.z = coords[index * 3 + 2];
            entry.previous = states.get(stateRefs[index]);
            index++;
            return true;
        }

        @Override
        public void close() {
        }
    }

    private static final class FileReader implements Reader {
        private final DataInputStream in;
        private final List<String> readStates = new ArrayList<>();

        FileReader(Path file) throws IOException {
            in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file), 1 << 16), 1 << 16));
        }

        @Override
        public boolean next(Entry entry) {
            try {
                while (true) {
                    int tag;
                    try {
                        tag = in.readByte();
                    } catch (EOFException eof) {
                        return false;
                    }
                    if (tag == TAG_STATE) {
                        readStates.add(in.readUTF());
                    } else if (tag == TAG_ENTRY) {
                        entry.x = in.readInt();
                        entry.y = in.readInt();
                        entry.z = in.readInt();
                        int ref = in.readInt();
                        if (ref < 0 || ref >= readStates.size()) {
                            throw new IOException("Corrupt undo journal: unknown state reference " + ref);
                        }
                        entry.previous = readStates.get(ref);
                        return true;
                    } else {
                        throw new IOException("Corrupt undo journal: unknown tag " + tag);
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to read undo journal.", ex);
            }
        }

        @Override
        public void close() {
            try {
                in.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
