package com.annaschneider.minecraft1.largebuild.image;

/** Domain-separated seeds; constants are stable protocol values, not enum ordinals or collection hashes. */
public final class SceneSeeds {
    private SceneSeeds() {}

    public static long selectionSeed(long resolvedSeed) {
        return mix(resolvedSeed ^ 0x73656c6563747631L);
    }

    public static long generatorSeed(long resolvedSeed, LayoutType layout) {
        long salt = switch (layout) {
            case RADIAL -> 0x72616469616c7631L;
            case LINEAR -> 0x6c696e6561727631L;
            case TERRACED -> 0x7465727261637631L;
            case RING -> 0x72696e672d2d7631L;
            case GRID -> 0x677269642d2d7631L;
            case CLIFF -> 0x636c6966662d7631L;
            case LEGACY -> 0x6c65676163797631L;
            case AUTO -> throw new IllegalArgumentException("Resolve AUTO before deriving a generator seed.");
        };
        return mix(resolvedSeed ^ 0x67656e6572617631L ^ salt);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
