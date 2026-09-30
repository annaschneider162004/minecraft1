package com.annaschneider.minecraft1.largebuild.image;

/**
 * Minimal, bounded image header parsing (signature + dimensions). No pixel decoding and no desktop AWT classes.
 */
final class ImageHeaders {
    record Info(String format, int width, int height) {
    }

    private ImageHeaders() {
    }

    static Info parse(byte[] h, int length) {
        if (length >= 24 && u8(h, 0) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return new Info("png", be32(h, 16), be32(h, 20));
        }
        if (length >= 10 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return new Info("gif", le16(h, 6), le16(h, 8));
        }
        if (length >= 4 && u8(h, 0) == 0xFF && u8(h, 1) == 0xD8) {
            return jpeg(h, length);
        }
        if (length >= 30 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
            && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return webp(h);
        }
        return null;
    }

    private static Info jpeg(byte[] h, int length) {
        int i = 2;
        while (i + 9 < length) {
            if (u8(h, i) != 0xFF) {
                i++;
                continue;
            }
            int marker = u8(h, i + 1);
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            int segment = be16(h, i + 2);
            boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) {
                return new Info("jpeg", be16(h, i + 7), be16(h, i + 5));
            }
            if (segment < 2) {
                break;
            }
            i += 2 + segment;
        }
        return new Info("jpeg", 0, 0);
    }

    private static Info webp(byte[] h) {
        if (h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == 'X') {
            return new Info("webp", 1 + le24(h, 24), 1 + le24(h, 27));
        }
        if (h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == ' ') {
            return new Info("webp", le16(h, 26) & 0x3FFF, le16(h, 28) & 0x3FFF);
        }
        return new Info("webp", 0, 0);
    }

    private static int u8(byte[] h, int i) {
        return h[i] & 0xFF;
    }

    private static int be16(byte[] h, int i) {
        return (u8(h, i) << 8) | u8(h, i + 1);
    }

    private static int be32(byte[] h, int i) {
        long v = ((long) u8(h, i) << 24) | ((long) u8(h, i + 1) << 16) | ((long) u8(h, i + 2) << 8) | u8(h, i + 3);
        return (int) Math.min(Integer.MAX_VALUE, v);
    }

    private static int le16(byte[] h, int i) {
        return u8(h, i) | (u8(h, i + 1) << 8);
    }

    private static int le24(byte[] h, int i) {
        return u8(h, i) | (u8(h, i + 1) << 8) | (u8(h, i + 2) << 16);
    }
}
