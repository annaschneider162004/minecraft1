package com.annaschneider.minecraft1.largebuild.blueprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lazily evaluated blueprint composed of {@link PlacedStructure}s. Only the sorted list of candidate section keys and a
 * chunk-column index are kept in memory; block data is generated one section at a time while building. Later
 * structures override earlier ones where they overlap.
 */
public final class ProceduralBlueprint implements BlueprintSource {
    public static final long DEFAULT_MAX_SECTIONS = 4_000_000L;

    private final String name;
    private final List<PlacedStructure> structures;
    private final BlockPalette palette = new BlockPalette();
    private final Bounds bounds;
    private final long[] sectionKeys;
    private final Map<Long, int[]> columnIndex;

    public ProceduralBlueprint(String name, List<PlacedStructure> structures) {
        this(name, structures, DEFAULT_MAX_SECTIONS);
    }

    public ProceduralBlueprint(String name, List<PlacedStructure> structures, long maxSections) {
        this.name = com.annaschneider.minecraft1.domain.BlueprintValidation.requireName(name);
        if (structures == null || structures.isEmpty()) {
            throw new IllegalArgumentException("A procedural blueprint needs at least one structure.");
        }
        this.structures = List.copyOf(structures);
        Bounds union = null;
        long[] keys = new long[1024];
        int keyCount = 0;
        Map<Long, List<Integer>> columns = new HashMap<>();
        for (int i = 0; i < this.structures.size(); i++) {
            PlacedStructure structure = this.structures.get(i);
            structure.generator().palette().forEach(palette::indexOf);
            Bounds b = structure.bounds();
            union = union == null ? b : union.union(b);
            long[] own = ChunkPartitioner.sectionsIntersecting(b, maxSections);
            if ((long) keyCount + own.length > maxSections) {
                throw new IllegalArgumentException("Blueprint exceeds the section limit of " + maxSections + ".");
            }
            if (keyCount + own.length > keys.length) {
                keys = Arrays.copyOf(keys, Math.max(keys.length * 2, keyCount + own.length));
            }
            System.arraycopy(own, 0, keys, keyCount, own.length);
            keyCount += own.length;
            for (int cx = Math.floorDiv(b.minX(), 16); cx <= Math.floorDiv(b.maxX(), 16); cx++) {
                for (int cz = Math.floorDiv(b.minZ(), 16); cz <= Math.floorDiv(b.maxZ(), 16); cz++) {
                    columns.computeIfAbsent(ChunkPartitioner.chunkColumnKey(cx, cz), k -> new ArrayList<>(2)).add(i);
                }
            }
        }
        this.bounds = union;
        this.sectionKeys = ChunkPartitioner.sortedUnique(keys, keyCount);
        this.columnIndex = new HashMap<>(columns.size() * 2);
        columns.forEach((column, list) -> columnIndex.put(column, list.stream().mapToInt(Integer::intValue).toArray()));
    }

    /** Sorted packed candidate section keys (copy), e.g. for splitting work between agents. */
    public long[] sectionKeys() {
        return sectionKeys.clone();
    }

    public List<PlacedStructure> structures() {
        return structures;
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
        return sectionKeys.length;
    }

    /** Evaluates one section into {@code target}. */
    public void fill(SectionKey key, BlockSection target) {
        target.reset(key);
        int[] candidates = columnIndex.get(ChunkPartitioner.chunkColumnKey(key.chunkX(), key.chunkZ()));
        if (candidates == null) {
            return;
        }
        Bounds sectionBounds = key.bounds();
        for (int index : candidates) {
            PlacedStructure structure = structures.get(index);
            Bounds b = structure.bounds();
            if (!b.intersects(sectionBounds)) {
                continue;
            }
            int x0 = Math.max(b.minX(), sectionBounds.minX());
            int x1 = Math.min(b.maxX(), sectionBounds.maxX());
            int y0 = Math.max(b.minY(), sectionBounds.minY());
            int y1 = Math.min(b.maxY(), sectionBounds.maxY());
            int z0 = Math.max(b.minZ(), sectionBounds.minZ());
            int z1 = Math.min(b.maxZ(), sectionBounds.maxZ());
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        String block = structure.blockAt(x, y, z);
                        if (block != null) {
                            target.set(BlockSection.index(x - key.minX(), y - key.minY(), z - key.minZ()), palette.indexOf(block));
                        }
                    }
                }
            }
        }
    }

    @Override
    public SectionCursor openCursor() {
        return new SectionCursor() {
            private int next;

            @Override
            public boolean next(BlockSection target) {
                if (next >= sectionKeys.length) {
                    return false;
                }
                fill(SectionKey.unpack(sectionKeys[next++]), target);
                return true;
            }

            @Override
            public void close() {
            }
        };
    }
}
