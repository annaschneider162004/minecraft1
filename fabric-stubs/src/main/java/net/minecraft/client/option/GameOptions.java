package net.minecraft.client.option;

/** Compile-time stub of {@code net.minecraft.client.option.GameOptions}. */
public class GameOptions {
    public boolean hudHidden;
    private Perspective perspective = Perspective.FIRST_PERSON;

    public Perspective getPerspective() {
        return perspective;
    }

    public void setPerspective(Perspective perspective) {
        this.perspective = perspective;
    }
}
