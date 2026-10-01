package net.minecraft.text;

public interface Text {
    String getString();

    static Text literal(String text) {
        return new LiteralText(text);
    }

    record LiteralText(String text) implements Text {
        @Override
        public String getString() {
            return text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
