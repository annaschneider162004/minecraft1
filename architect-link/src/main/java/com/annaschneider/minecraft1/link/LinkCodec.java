package com.annaschneider.minecraft1.link;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** JSON encoding of link messages (one object per line) and a length-bounded line reader. */
public final class LinkCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LinkCodec() {
    }

    public static String encode(Object message) {
        // Gson never emits raw newlines (they are escaped inside strings), so one object is exactly one line.
        return GSON.toJson(message);
    }

    public static LinkRequest decodeRequest(String line) {
        return decode(line, LinkRequest.class);
    }

    public static LinkMessage decodeMessage(String line) {
        return decode(line, LinkMessage.class);
    }

    public static <T> T decode(String line, Class<T> type) {
        if (line == null || line.isBlank()) {
            throw new LinkProtocolException("Empty message.");
        }
        try {
            T value = GSON.fromJson(line, type);
            if (value == null) {
                throw new LinkProtocolException("Empty message.");
            }
            return value;
        } catch (JsonParseException | IllegalArgumentException ex) {
            throw new LinkProtocolException("Malformed message: expected one JSON object per line.");
        }
    }

    public static Gson gson() {
        return GSON;
    }

    /**
     * Reads one {@code \n}-terminated UTF-8 line (a trailing {@code \r} is dropped). Returns {@code null} at end of
     * stream. Lines longer than {@code maxBytes} raise {@link LinkProtocolException} so a peer cannot exhaust memory.
     */
    public static String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return toLine(buffer);
            }
            if (buffer.size() >= maxBytes) {
                throw new LinkProtocolException("Message is larger than " + maxBytes + " bytes.");
            }
            buffer.write(b);
        }
        return buffer.size() == 0 ? null : toLine(buffer);
    }

    private static String toLine(ByteArrayOutputStream buffer) {
        String line = buffer.toString(StandardCharsets.UTF_8);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
