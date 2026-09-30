package com.annaschneider.minecraft1.largebuild.blueprint;

import com.annaschneider.minecraft1.domain.BlueprintValidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps block ids to compact {@code short} indices. Index {@link #EMPTY} (0) means "leave the world untouched".
 */
public final class BlockPalette {
    public static final short EMPTY = 0;
    public static final int MAX_ENTRIES = Short.MAX_VALUE;

    private final List<String> ids = new ArrayList<>();
    private final Map<String, Short> indices = new HashMap<>();

    public BlockPalette() {
        ids.add(null);
    }

    public synchronized short indexOf(String blockId) {
        String normalized = BlueprintValidation.requireBlockId(blockId);
        Short existing = indices.get(normalized);
        if (existing != null) {
            return existing;
        }
        if (ids.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("Block palette is full (" + MAX_ENTRIES + " entries).");
        }
        short index = (short) ids.size();
        ids.add(normalized);
        indices.put(normalized, index);
        return index;
    }

    public synchronized String blockId(short index) {
        if (index <= 0 || index >= ids.size()) {
            return null;
        }
        return ids.get(index);
    }

    /** Number of entries including the reserved empty slot. */
    public synchronized int size() {
        return ids.size();
    }

    /** Real block ids in index order (excludes the reserved empty slot). */
    public synchronized List<String> blockIds() {
        return Collections.unmodifiableList(new ArrayList<>(ids.subList(1, ids.size())));
    }
}
