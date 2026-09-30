package com.annaschneider.minecraft1.largebuild.blueprint;

import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.generator.StructureGenerator;

import java.util.Objects;

/**
 * A generator positioned inside a scene with an offset and a rotation/mirror transform.
 */
public final class PlacedStructure {
    private final StructureGenerator generator;
    private final Vec3i offset;
    private final Transform transform;
    private final Bounds localBounds;
    private final Bounds bounds;

    public PlacedStructure(StructureGenerator generator, Vec3i offset, Transform transform) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.offset = Objects.requireNonNull(offset, "offset");
        this.transform = transform == null ? Transform.IDENTITY : transform;
        this.localBounds = generator.localBounds();
        this.bounds = this.transform.apply(localBounds).translated(offset.x(), offset.y(), offset.z());
    }

    public StructureGenerator generator() {
        return generator;
    }

    public Vec3i offset() {
        return offset;
    }

    public Transform transform() {
        return transform;
    }

    /** Bounds in blueprint (scene) coordinates. */
    public Bounds bounds() {
        return bounds;
    }

    /** Block at a blueprint coordinate, or {@code null}. */
    public String blockAt(int x, int y, int z) {
        int rx = x - offset.x();
        int rz = z - offset.z();
        int lx = transform.inverseX(rx, rz);
        int lz = transform.inverseZ(rx, rz);
        int ly = y - offset.y();
        if (!localBounds.contains(lx, ly, lz)) {
            return null;
        }
        return generator.blockAt(lx, ly, lz);
    }
}
