package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.domain.MirrorAxis;
import com.annaschneider.minecraft1.domain.Vec3i;
import com.annaschneider.minecraft1.largebuild.blueprint.BlockPalette;
import com.annaschneider.minecraft1.largebuild.blueprint.BlockSection;
import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.PlacedStructure;
import com.annaschneider.minecraft1.largebuild.blueprint.ProceduralBlueprint;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionCursor;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionKey;
import com.annaschneider.minecraft1.largebuild.blueprint.Transform;
import com.annaschneider.minecraft1.largebuild.generator.BridgeGenerator;
import com.annaschneider.minecraft1.largebuild.generator.CherryGroveGenerator;
import com.annaschneider.minecraft1.largebuild.generator.IslandGenerator;
import com.annaschneider.minecraft1.largebuild.generator.PalaceCoreGenerator;
import com.annaschneider.minecraft1.largebuild.generator.StructureGenerator;
import com.annaschneider.minecraft1.largebuild.generator.WaterfallGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProceduralBlueprintTest {
    @Test
    void streamedSectionsMatchBruteForceComposition() {
        List<PlacedStructure> structures = List.of(
            new PlacedStructure(new IslandGenerator(14, 10, 42), new Vec3i(3, 0, -5), Transform.IDENTITY),
            new PlacedStructure(new BridgeGenerator(30, 2, 6), new Vec3i(0, 0, 0), Transform.of(90, MirrorAxis.NONE)),
            new PlacedStructure(new WaterfallGenerator(5, 12, 9), new Vec3i(-7, 0, 2), Transform.of(270, MirrorAxis.X)),
            new PlacedStructure(new CherryGroveGenerator(8, 3), new Vec3i(5, 0, -3), Transform.of(180, MirrorAxis.Z)));
        ProceduralBlueprint blueprint = new ProceduralBlueprint("test", structures);
        BlockPalette palette = blueprint.palette();

        Set<Long> seen = new HashSet<>();
        long nonEmpty = 0;
        try (SectionCursor cursor = blueprint.openCursor()) {
            BlockSection section = new BlockSection();
            while (cursor.next(section)) {
                SectionKey key = section.key();
                assertTrue(seen.add(key.pack()), "section emitted twice: " + key);
                for (int i = 0; i < SectionKey.VOLUME; i++) {
                    int x = key.minX() + BlockSection.localX(i);
                    int y = key.minY() + BlockSection.localY(i);
                    int z = key.minZ() + BlockSection.localZ(i);
                    String expected = expected(structures, x, y, z);
                    String actual = palette.blockId(section.get(i));
                    assertEquals(expected, actual, "at " + x + "," + y + "," + z);
                    if (actual != null) {
                        nonEmpty++;
                    }
                }
            }
        }
        long bruteForce = 0;
        Bounds b = blueprint.bounds();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    if (expected(structures, x, y, z) != null) {
                        bruteForce++;
                        assertTrue(seen.contains(SectionKey.containing(x, y, z).pack()));
                    }
                }
            }
        }
        assertEquals(bruteForce, nonEmpty);
        assertTrue(nonEmpty > 1_000);
        assertEquals(seen.size(), blueprint.sectionCount());
    }

    @Test
    void rotatedStructureOccupiesRotatedFootprint() {
        StructureGenerator bridge = new BridgeGenerator(40, 2, 4);
        PlacedStructure alongZ = new PlacedStructure(bridge, new Vec3i(100, 0, 100), Transform.of(90, MirrorAxis.NONE));
        assertEquals(bridge.localBounds().sizeX(), alongZ.bounds().sizeZ());
        assertNotNull(alongZ.blockAt(100, 0, 139));
        assertEquals(null, alongZ.blockAt(139, 0, 100));
    }

    @Test
    void sectionLimitIsEnforced() {
        List<PlacedStructure> structures = List.of(
            new PlacedStructure(new PalaceCoreGenerator(64, 120), new Vec3i(0, 0, 0), Transform.IDENTITY));
        assertThrows(IllegalArgumentException.class, () -> new ProceduralBlueprint("big", structures, 10));
    }

    private static String expected(List<PlacedStructure> structures, int x, int y, int z) {
        for (int i = structures.size() - 1; i >= 0; i--) {
            PlacedStructure s = structures.get(i);
            if (s.bounds().contains(x, y, z)) {
                String block = s.blockAt(x, y, z);
                if (block != null) {
                    return block;
                }
            }
        }
        return null;
    }
}
