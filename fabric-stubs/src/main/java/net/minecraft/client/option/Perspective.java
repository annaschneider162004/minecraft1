package net.minecraft.client.option;

/** Compile-time stub of {@code net.minecraft.client.option.Perspective}. */
public enum Perspective {
    FIRST_PERSON,
    THIRD_PERSON_BACK,
    THIRD_PERSON_FRONT;

    public boolean isFirstPerson() {
        return this == FIRST_PERSON;
    }
}
