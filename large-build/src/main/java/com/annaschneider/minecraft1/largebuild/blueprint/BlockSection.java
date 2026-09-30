package com.annaschneider.minecraft1.largebuild.blueprint;

import java.util.Arrays;

/**
 * Mutable, reusable 16x16x16 buffer of palette indices. Only one (or a few) of these exist per active job, which keeps
 * memory flat regardless of the total structure size.
 */
public final class BlockSection {
    private final short[] data = new short[SectionKey.VOLUME];
    private SectionKey key;
    private int nonEmpty;

    public void reset(SectionKey key) {
        this.key = key;
        Arrays.fill(data, BlockPalette.EMPTY);
        nonEmpty = 0;
    }

    public SectionKey key() {
        return key;
    }

    public static int index(int localX, int localY, int localZ) {
        return (localY << 8) | (localZ << 4) | localX;
    }

    public short get(int index) {
        return data[index];
    }

    public void set(int index, short value) {
        short previous = data[index];
        if (previous == BlockPalette.EMPTY && value != BlockPalette.EMPTY) {
            nonEmpty++;
        } else if (previous != BlockPalette.EMPTY && value == BlockPalette.EMPTY) {
            nonEmpty--;
        }
        data[index] = value;
    }

    public int nonEmptyCount() {
        return nonEmpty;
    }

    public boolean isEmpty() {
        return nonEmpty == 0;
    }

    public void copyFrom(SectionKey key, short[] source) {
        if (source.length != SectionKey.VOLUME) {
            throw new IllegalArgumentException("Section data must contain " + SectionKey.VOLUME + " entries.");
        }
        this.key = key;
        System.arraycopy(source, 0, data, 0, SectionKey.VOLUME);
        int count = 0;
        for (short value : data) {
            if (value != BlockPalette.EMPTY) {
                count++;
            }
        }
        nonEmpty = count;
    }

    public short[] copyData() {
        return data.clone();
    }

    public static int localX(int index) {
        return index & 15;
    }

    public static int localZ(int index) {
        return (index >> 4) & 15;
    }

    public static int localY(int index) {
        return (index >> 8) & 15;
    }
}
