package net.minecraft.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PacketByteBuf {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private ByteArrayInputStream input;
    private DataInputStream dataInput;

    public PacketByteBuf() {}

    public PacketByteBuf(byte[] bytes) {
        this.input = new ByteArrayInputStream(bytes);
        this.dataInput = new DataInputStream(input);
    }

    public PacketByteBuf writeString(String str) {
        try {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            DataOutputStream dos = new DataOutputStream(output);
            dos.writeInt(bytes.length);
            dos.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public String readString() {
        try {
            if (dataInput == null) {
                dataInput = new DataInputStream(new ByteArrayInputStream(output.toByteArray()));
            }
            int len = dataInput.readInt();
            byte[] bytes = new byte[len];
            dataInput.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PacketByteBuf writeInt(int val) {
        try {
            DataOutputStream dos = new DataOutputStream(output);
            dos.writeInt(val);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public int readInt() {
        try {
            if (dataInput == null) {
                dataInput = new DataInputStream(new ByteArrayInputStream(output.toByteArray()));
            }
            return dataInput.readInt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PacketByteBuf writeBoolean(boolean val) {
        try {
            DataOutputStream dos = new DataOutputStream(output);
            dos.writeBoolean(val);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public boolean readBoolean() {
        try {
            if (dataInput == null) {
                dataInput = new DataInputStream(new ByteArrayInputStream(output.toByteArray()));
            }
            return dataInput.readBoolean();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }

    public PacketByteBuf copy() {
        return new PacketByteBuf(toByteArray());
    }
}
