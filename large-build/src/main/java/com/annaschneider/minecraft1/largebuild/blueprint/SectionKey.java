package com.annaschneider.minecraft1.largebuild.blueprint;

/**
 * Identifies a 16x16x16 section (chunk X, section Y, chunk Z).
 * <p>
 * Keys pack into a single {@code long} whose natural ordering is (chunkX, chunkZ, sectionY), so sorting packed keys
 * yields a chunk-column-major, bottom-to-top build order.
 */
public record SectionKey(int chunkX, int sectionY, int chunkZ) implements Comparable<SectionKey> {
    public static final int SIZE = 16;
    public static final int VOLUME = SIZE * SIZE * SIZE;

    private static final int XZ_BITS = 22;
    private static final int Y_BITS = 20;
    private static final long XZ_OFFSET = 1L << (XZ_BITS - 1);
    private static final long Y_OFFSET = 1L << (Y_BITS - 1);
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;

    public SectionKey {
        if (Math.abs((long) chunkX) >= XZ_OFFSET || Math.abs((long) chunkZ) >= XZ_OFFSET || Math.abs((long) sectionY) >= Y_OFFSET) {
            throw new IllegalArgumentException("Section coordinates out of supported range.");
        }
    }

    public static SectionKey containing(int x, int y, int z) {
        return new SectionKey(Math.floorDiv(x, SIZE), Math.floorDiv(y, SIZE), Math.floorDiv(z, SIZE));
    }

    public long pack() {
        long raw = ((chunkX + XZ_OFFSET) << (XZ_BITS + Y_BITS))
            | ((chunkZ + XZ_OFFSET) << Y_BITS)
            | (sectionY + Y_OFFSET);
        // flip the sign bit so that signed long ordering equals unsigned (lexicographic) ordering
        return raw ^ Long.MIN_VALUE;
    }

    public static SectionKey unpack(long packed) {
        packed ^= Long.MIN_VALUE;
        int x = (int) (((packed >>> (XZ_BITS + Y_BITS)) & XZ_MASK) - XZ_OFFSET);
        int z = (int) (((packed >>> Y_BITS) & XZ_MASK) - XZ_OFFSET);
        int y = (int) ((packed & Y_MASK) - Y_OFFSET);
        return new SectionKey(x, y, z);
    }

    public int minX() {
        return chunkX * SIZE;
    }

    public int minY() {
        return sectionY * SIZE;
    }

    public int minZ() {
        return chunkZ * SIZE;
    }

    public Bounds bounds() {
        return Bounds.ofSize(minX(), minY(), minZ(), SIZE, SIZE, SIZE);
    }

    @Override
    public int compareTo(SectionKey other) {
        return Long.compare(pack(), other.pack());
    }
}
