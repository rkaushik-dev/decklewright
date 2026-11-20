package io.github.rkaushikdev.decklewright.testing;

import java.nio.charset.StandardCharsets;

/**
 * Byte arrays written the way a test reads them: as a list of numbers, as text of one character a
 * byte, and back.
 */
public final class TestBytes {
    private TestBytes() {}

    /** The values as bytes, each keeping its low eight bits. */
    public static byte[] u8(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i += 1) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    /** Every byte as the unsigned value it holds, so arrays compare as lists of numbers. */
    public static int[] unsigned(byte[] bytes) {
        int[] out = new int[bytes.length];
        for (int i = 0; i < bytes.length; i += 1) {
            out[i] = bytes[i] & 0xff;
        }
        return out;
    }

    /** One byte per character, each keeping its low eight bits. */
    public static byte[] ascii(String text) {
        byte[] out = new byte[text.length()];
        for (int i = 0; i < text.length(); i += 1) {
            out[i] = (byte) text.charAt(i);
        }
        return out;
    }

    /** One character per byte. */
    public static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
