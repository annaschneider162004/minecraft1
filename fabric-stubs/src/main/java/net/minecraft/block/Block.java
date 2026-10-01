package net.minecraft.block;

public class Block {
    public static final int NOTIFY_NEIGHBORS = 1;
    public static final int NOTIFY_LISTENERS = 2;
    public static final int NO_REDRAW = 4;
    public static final int REDRAW_ON_MAIN_THREAD = 8;
    public static final int FORCE_STATE = 16;
    public static final int SKIP_DROPS = 32;
    public static final int MOVED = 64;
    public static final int NOTIFY_ALL = 3;

    private final BlockState defaultState;

    public Block() {
        this.defaultState = new BlockState(this);
    }

    public BlockState getDefaultState() {
        return defaultState;
    }
}
