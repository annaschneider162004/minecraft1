package net.minecraft.entity;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Compile-time stub of {@code net.minecraft.entity.Entity} (only the members the mod uses). */
public class Entity {
    public double prevX;
    public double prevY;
    public double prevZ;
    public float prevYaw;
    public float prevPitch;

    private final World world;
    private final UUID uuid = UUID.randomUUID();
    private final Set<String> commandTags = new HashSet<>();
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean removed;
    private Text customName;

    public Entity(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    public UUID getUuid() {
        return uuid;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch) {
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.prevYaw = yaw;
        this.prevPitch = pitch;
        setPos(x, y, z);
        setYaw(yaw);
        setPitch(pitch);
    }

    public BlockPos getBlockPos() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public boolean addCommandTag(String tag) {
        return commandTags.add(tag);
    }

    public Set<String> getCommandTags() {
        return commandTags;
    }

    public void setCustomName(Text name) {
        this.customName = name;
    }

    public Text getCustomName() {
        return customName;
    }

    public void setCustomNameVisible(boolean visible) {
    }

    public void setInvulnerable(boolean invulnerable) {
    }

    public void setSilent(boolean silent) {
    }

    public void setNoGravity(boolean noGravity) {
    }

    public void setInvisible(boolean invisible) {
    }

    public void discard() {
        removed = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isAlive() {
        return !removed;
    }
}
