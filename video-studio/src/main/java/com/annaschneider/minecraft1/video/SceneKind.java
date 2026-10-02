package com.annaschneider.minecraft1.video;

/** Role of a scene in the generated story. */
public enum SceneKind {
    /** Opening shot at the start of the build. */
    INTRO,
    /** Real-time look at one moment of the build. */
    PROGRESS,
    /** A sped-up stretch of the build. */
    TIMELAPSE,
    /** The finished build. */
    FINALE
}
