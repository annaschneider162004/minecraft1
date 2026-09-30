package com.annaschneider.minecraft1.largebuild.engine;

public enum JobState {
    QUEUED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
