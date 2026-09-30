package com.annaschneider.minecraft1.largebuild.image;

import com.annaschneider.minecraft1.largebuild.scene.ScenePlan;

/**
 * @param scale 1..24; number of island rings around the palace (scale 1 is roughly a few hundred thousand blocks,
 *              the maximum reaches tens of millions)
 */
public record PlanOptions(String planId, int scale) {
    public static final int MAX_SCALE = 24;

    public PlanOptions {
        ScenePlan.requireId(planId);
        if (scale < 1 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Scale must be in range 1.." + MAX_SCALE + ".");
        }
    }
}
