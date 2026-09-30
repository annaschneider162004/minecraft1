package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.domain.Blueprint;
import com.annaschneider.minecraft1.domain.BlueprintBlock;
import com.annaschneider.minecraft1.domain.MirrorAxis;
import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.ChunkPartitioner;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionKey;
import com.annaschneider.minecraft1.largebuild.blueprint.Transform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintModelTest {
    @Test
    void sectionKeysRoundTripAndSortColumnMajorIncludingNegatives() {
        List<SectionKey> keys = List.of(new SectionKey(-3, 2, 5), new SectionKey(-3, -4, 5), new SectionKey(0, 0, -1),
            new SectionKey(0, 0, 0), new SectionKey(7, -1, -9), new SectionKey(-100000, 19, 100000));
        for (SectionKey key : keys) {
            assertEquals(key, SectionKey.unpack(key.pack()));
        }
        for (SectionKey a : keys) {
            for (SectionKey b : keys) {
                int expected = a.chunkX() != b.chunkX() ? Integer.compare(a.chunkX(), b.chunkX())
                    : a.chunkZ() != b.chunkZ() ? Integer.compare(a.chunkZ(), b.chunkZ())
                    : Integer.compare(a.sectionY(), b.sectionY());
                assertEquals(Integer.signum(expected), Integer.signum(Long.compare(a.pack(), b.pack())), a + " vs " + b);
            }
        }
        assertEquals(new SectionKey(-1, -1, -1), SectionKey.containing(-1, -1, -1));
        assertEquals(new SectionKey(0, 1, 1), SectionKey.containing(15, 16, 31));
    }

    @Test
    void partitionerFindsExactlyTheIntersectingSections() {
        Bounds bounds = new Bounds(-17, -3, 5, 40, 33, 20);
        long[] keys = ChunkPartitioner.sectionsIntersecting(bounds, 10_000);
        Set<SectionKey> expected = new HashSet<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    expected.add(SectionKey.containing(x, y, z));
                }
            }
        }
        assertEquals(expected.size(), keys.length);
        assertEquals(expected.size(), ChunkPartitioner.sectionCount(bounds));
        for (int i = 0; i < keys.length; i++) {
            assertTrue(expected.contains(SectionKey.unpack(keys[i])));
            if (i > 0) {
                assertTrue(keys[i - 1] < keys[i], "keys must be strictly ascending");
            }
        }
        assertEquals(5L * 2, ChunkPartitioner.chunkColumnCount(bounds));
    }

    @Test
    void transformMatchesDomainBlueprintMirrorThenRotate() {
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (int x = -2; x <= 3; x++) {
            for (int z = -1; z <= 4; z++) {
                blocks.add(new BlueprintBlock(new Vec3i(x, x + z, z), "minecraft:stone"));
            }
        }
        Blueprint source = Blueprint.of("t", blocks);
        for (MirrorAxis mirror : MirrorAxis.values()) {
            for (int rotation = 0; rotation < 360; rotation += 90) {
                Transform transform = Transform.of(rotation, mirror);
                Blueprint expected = source.mirrored(mirror).rotated(rotation);
                for (int i = 0; i < blocks.size(); i++) {
                    Vec3i p = blocks.get(i).position();
                    Vec3i e = expected.blocks().get(i).position();
                    int tx = transform.applyX(p.x(), p.z());
                    int tz = transform.applyZ(p.x(), p.z());
                    assertEquals(e.x(), tx);
                    assertEquals(e.z(), tz);
                    assertEquals(p.x(), transform.inverseX(tx, tz));
                    assertEquals(p.z(), transform.inverseZ(tx, tz));
                }
            }
        }
    }

    @Test
    void transformedBoundsContainAllTransformedPoints() {
        Bounds local = new Bounds(-3, 0, 2, 10, 4, 7);
        for (MirrorAxis mirror : MirrorAxis.values()) {
            for (int rotation = 0; rotation < 360; rotation += 90) {
                Transform t = Transform.of(rotation, mirror);
                Bounds world = t.apply(local);
                assertEquals(local.volume(), world.volume());
                for (int x = local.minX(); x <= local.maxX(); x++) {
                    for (int z = local.minZ(); z <= local.maxZ(); z++) {
                        assertTrue(world.contains(t.applyX(x, z), 0, t.applyZ(x, z)));
                    }
                }
            }
        }
    }
}
