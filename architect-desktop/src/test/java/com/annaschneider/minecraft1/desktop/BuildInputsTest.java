package com.annaschneider.minecraft1.desktop;

import com.annaschneider.minecraft1.link.LinkProtocol;
import com.annaschneider.minecraft1.link.LinkRequest;
import com.annaschneider.minecraft1.link.RequestType;
import com.annaschneider.minecraft1.link.RequestValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildInputsTest {
    @Test
    void suggestsShortValidPlanNames() {
        assertEquals("white-palace-with-waterfalls", BuildInputs.suggestPlanName("White palace with waterfalls, bridges and cherry trees"));
        assertEquals("my-build", BuildInputs.suggestPlanName("   "));
        assertEquals("lau-dai-trang", BuildInputs.suggestPlanName("L\u00e2u \u0111\u00e0i tr\u1eafng"));
        String longWord = BuildInputs.suggestPlanName("x".repeat(80));
        assertEquals(32, longWord.length());
        assertTrue(BuildInputs.isValidPlanName(longWord));
        assertFalse(BuildInputs.isValidPlanName("Sky Palace"));
        assertFalse(BuildInputs.isValidPlanName(""));
        assertFalse(BuildInputs.isValidPlanName(null));
    }

    @Test
    void checksImagesBeforeUpload(@TempDir Path dir) throws IOException {
        assertTrue(BuildInputs.checkImage(null).contains("Choose a picture"));
        assertTrue(BuildInputs.checkImage(dir.resolve("missing.png")).contains("Choose a picture"));
        Path text = Files.writeString(dir.resolve("notes.txt"), "hi");
        assertTrue(BuildInputs.checkImage(text).contains("not supported"));
        Path empty = Files.createFile(dir.resolve("empty.png"));
        assertTrue(BuildInputs.checkImage(empty).contains("empty"));
        Path huge = dir.resolve("huge.jpg");
        try (RandomAccessFile file = new RandomAccessFile(huge.toFile(), "rw")) {
            file.setLength(LinkProtocol.MAX_IMAGE_BYTES + 1);
        }
        assertTrue(BuildInputs.checkImage(huge).contains("too large"));
        assertThrows(IOException.class, () -> BuildInputs.uploadRequest(huge));
    }

    @Test
    void uploadRequestUsesSafeNameAndValidates(@TempDir Path dir) throws IOException {
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        Path image = Files.write(dir.resolve("Sky Palace (1).PNG"), bytes);
        assertNull(BuildInputs.checkImage(image));
        LinkRequest request = BuildInputs.uploadRequest(image);
        assertEquals(RequestType.UPLOAD_IMAGE, request.type());
        assertEquals("sky-palace-1.png", request.fileName());
        assertArrayEquals(bytes, Base64.getDecoder().decode(request.data()));
        RequestValidator.validate(request.withId("1"));
    }

    @Test
    void defaultLinkFileFollowsTheMinecraftFolder() {
        assertEquals(Path.of("C:\\Users\\anna\\AppData\\Roaming", ".minecraft", "config", "architect", "desktop-link.json"),
            LinkFileLocator.defaultLinkFile("Windows 11", "C:\\Users\\anna\\AppData\\Roaming", "C:\\Users\\anna"));
        assertEquals(Path.of("/home/anna", ".minecraft", "config", "architect", "desktop-link.json"),
            LinkFileLocator.defaultLinkFile("Linux", null, "/home/anna"));
        assertEquals(Path.of("/Users/anna", "Library", "Application Support", "minecraft", "config", "architect", "desktop-link.json"),
            LinkFileLocator.defaultLinkFile("Mac OS X", null, "/Users/anna"));
    }
}
