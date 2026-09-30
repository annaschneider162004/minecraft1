package com.annaschneider.minecraft1.largebuild.blueprint;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBlock;
import com.annaschneider.minecraft1.domain.Vec3i;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sparse in-memory blueprint stored as palette-indexed 16^3 sections (8 KiB per non-empty section). Suited for small
 * and medium structures and for converting classic {@link Blueprint}s; huge scenes should use
 * {@link ProceduralBlueprint} or a stored blueprint instead.
 */
public final class SectionedBlueprint implements BlueprintSource {
    private final String name;
    private final BlockPalette palette;
    private final TreeMap<Long, short[]> sections = new TreeMap<>();
    private Bounds bounds;

    public SectionedBlueprint(String name) {
        this(name, new BlockPalette());
    }

    public SectionedBlueprint(String name, BlockPalette palette) {
        this.name = com.annaschneider.minecraft1.domain.BlueprintValidation.requireName(name);
        this.palette = palette;
    }

    public static SectionedBlueprint fromBlueprint(Blueprint blueprint) {
        SectionedBlueprint result = new SectionedBlueprint(blueprint.name());
        for (BlueprintBlock block : blueprint.blocks()) {
            Vec3i p = block.position();
            result.set(p.x(), p.y(), p.z(), block.blockId());
        }
        return result;
    }

    public void set(int x, int y, int z, String blockId) {
        SectionKey key = SectionKey.containing(x, y, z);
        short[] data = sections.computeIfAbsent(key.pack(), k -> new short[SectionKey.VOLUME]);
        data[BlockSection.index(x - key.minX(), y - key.minY(), z - key.minZ())] = palette.indexOf(blockId);
        Bounds point = new Bounds(x, y, z, x, y, z);
        bounds = bounds == null ? point : bounds.union(point);
    }

    public void putSection(SectionKey key, short[] data) {
        if (data.length != SectionKey.VOLUME) {
            throw new IllegalArgumentException("Section data must contain " + SectionKey.VOLUME + " entries.");
        }
        sections.put(key.pack(), data.clone());
        Bounds sectionBounds = key.bounds();
        bounds = bounds == null ? sectionBounds : bounds.union(sectionBounds);
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
        if (bounds == null) {
            throw new IllegalStateException("Blueprint is empty.");
        }
        return bounds;
    }

    @Override
    public long sectionCount() {
        return sections.size();
    }

    @Override
    public SectionCursor openCursor() {
        Iterator<Map.Entry<Long, short[]>> iterator = sections.entrySet().iterator();
        return new SectionCursor() {
            @Override
            public boolean next(BlockSection target) {
                if (!iterator.hasNext()) {
                    return false;
                }
                Map.Entry<Long, short[]> entry = iterator.next();
                target.copyFrom(SectionKey.unpack(entry.getKey()), entry.getValue());
                return true;
            }

            @Override
            public void close() {
            }
        };
    }
}
