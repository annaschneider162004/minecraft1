package com.annaschneider.minecraft1.largebuild;

import com.annaschneider.minecraft1.largebuild.blueprint.Bounds;
import com.annaschneider.minecraft1.largebuild.blueprint.ChunkPartitioner;
import com.annaschneider.minecraft1.largebuild.blueprint.SectionKey;
import com.annaschneider.minecraft1.largebuild.camera.CameraKeyframe;
import com.annaschneider.minecraft1.largebuild.camera.CameraPath;
import com.annaschneider.minecraft1.largebuild.camera.DeterministicShotPlanner;
import com.annaschneider.minecraft1.largebuild.camera.ShotType;
import com.annaschneider.minecraft1.largebuild.npc.AgentRole;
import com.annaschneider.minecraft1.largebuild.npc.AgentTask;
import com.annaschneider.minecraft1.largebuild.npc.AgentWorkPartitioner;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCameraTest {
    @Test
    void agentsGetBalancedDisjointSlicesWithoutSharingChunkColumns() {
        long[] keys = ChunkPartitioner.sectionsIntersecting(new Bounds(-100, 0, -50, 100, 40, 60), 100_000);
        List<AgentRole> roles = List.of(AgentRole.BUILDER, AgentRole.BUILDER, AgentRole.DECORATOR, AgentRole.ROAD_WORKER);
        List<AgentTask> tasks = AgentWorkPartitioner.partition(7, keys, roles);
        assertEquals(4, tasks.size());
        int total = 0;
        Set<Long> columns = new HashSet<>();
        for (AgentTask task : tasks) {
            total += task.sectionCount();
            assertTrue(task.sectionCount() > keys.length / 8, "roughly balanced");
            Set<Long> own = new HashSet<>();
            for (long key : task.sectionKeys()) {
                SectionKey k = SectionKey.unpack(key);
                own.add(ChunkPartitioner.chunkColumnKey(k.chunkX(), k.chunkZ()));
            }
            for (long column : own) {
                assertTrue(columns.add(column), "column shared between agents");
            }
        }
        assertEquals(keys.length, total);
        assertThrows(IllegalArgumentException.class, () -> AgentWorkPartitioner.partition(1, keys, List.of()));
    }

    @Test
    void shotsFrameTheTarget() {
        Bounds target = new Bounds(-50, 0, -30, 50, 80, 30);
        DeterministicShotPlanner planner = new DeterministicShotPlanner();
        for (ShotType type : ShotType.values()) {
            CameraPath path = planner.plan(type, target, 30);
            assertEquals(30.0, path.durationSeconds(), 1e-9);
            for (CameraKeyframe frame : path.keyframes()) {
                assertFalse(target.contains((int) Math.floor(frame.x()), (int) Math.floor(frame.y()), (int) Math.floor(frame.z())),
                    type + " camera must stay outside the structure");
            }
        }
        CameraPath orbit = planner.plan(ShotType.ORBIT, target, 24);
        CameraKeyframe start = orbit.keyframes().get(0);
        assertTrue(start.x() > target.maxX());
        assertEquals(90f, Math.abs(start.yaw()), 0.5f, "camera on +X side looks toward -X");
        CameraKeyframe mid = orbit.sample(0.5);
        assertTrue(mid.x() < start.x());
        assertEquals(ShotType.TOP_DOWN, ShotType.fromId("top-down"));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(ShotType.ORBIT, target, 0));
    }
}
