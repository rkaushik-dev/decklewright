package io.github.rkaushikdev.decklewright.codec.tiff;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tiff flavour of lzw.
 *
 * <p>Codes are packed most significant bit first, the width grows one bit early compared with the gif
 * version, and the dictionary is cleared at code two five six. That early change is the part every
 * implementation gets wrong once, and the symptom is a strip that decodes correctly for a few hundred
 * bytes and then turns to noise.
 */
public final class Lzw {
    private static final int CLEAR = 256;
    private static final int END = 257;
    private static final int FIRST = 258;
    private static final int MAX_WIDTH = 12;

    /** The table never grows past this many codes; a well behaved encoder clears before it does. */
    private static final int TABLE_LIMIT = 4094;

    private Lzw() {}

    /**
     * The data decoded, however long that comes to. Decoding stops at the end code, or quietly when
     * the data runs out in the middle of a code.
     *
     * @throws CodecError when the data holds a code the table does not have
     */
    public static byte[] lzwDecode(byte[] bytes) {
        return decode(bytes, 0, bytes.length, StripOutput.unclaimed());
    }

    /**
     * The data decoded, checked against how long the strip claims it is.
     *
     * @throws CodecError when the data holds a code the table does not have, or decodes to more or
     *     fewer bytes than expected
     * @throws NegativeArraySizeException when the expected length is negative
     */
    public static byte[] lzwDecode(byte[] bytes, int expected) {
        return decode(bytes, 0, bytes.length, StripOutput.claimed(new byte[expected]));
    }

    /**
     * The bytes from {@code from} up to {@code to} decoded straight into {@code into}, which must come
     * out exactly full, so a strip is read without copying it out of the file first.
     *
     * @return {@code into}
     * @throws CodecError when the data holds a code the table does not have, or decodes to more or
     *     fewer bytes than {@code into} holds
     */
    static byte[] lzwDecode(byte[] bytes, int from, int to, byte[] into) {
        return decode(bytes, from, to, StripOutput.claimed(into));
    }

    /**
     * A matching encoder. The lab never writes lzw, but a decoder with no encoder can only ever be
     * tested against fixtures somebody else made, and then a disagreement tells you nothing about
     * which side is wrong.
     */
    public static byte[] lzwEncode(byte[] bytes) {
        BitWriter writer = new BitWriter();
        // a string of more than one byte is keyed by its prefix's code and its last byte; a single
        // byte is its own code and is never stored
        Map<Integer, Integer> table = new HashMap<>();
        int next = FIRST;
        int width = 9;
        writer.write(CLEAR, width);

        int current = -1;
        for (byte value : bytes) {
            int symbol = value & 0xff;
            if (current < 0) {
                current = symbol;
                continue;
            }
            Integer known = table.get((current << 8) | symbol);
            if (known != null) {
                current = known;
                continue;
            }
            writer.write(current, width);
            table.put((current << 8) | symbol, next);
            next += 1;
            width = Math.min(MAX_WIDTH, widthFor(next));
            if (next >= TABLE_LIMIT) {
                writer.write(CLEAR, width);
                table.clear();
                next = FIRST;
                width = 9;
            }
            current = symbol;
        }
        if (current >= 0) {
            writer.write(current, width);
        }
        writer.write(END, width);
        return writer.finish();
    }

    private static byte[] decode(byte[] bytes, int from, int to, StripOutput out) {
        BitReader reader = new BitReader(bytes, from, to);
        byte[][] table = new byte[1 << MAX_WIDTH][];
        for (int i = 0; i < 256; i += 1) {
            table[i] = new byte[] {(byte) i};
        }
        int size = FIRST;
        int width = 9;
        byte[] previous = null;

        for (; ; ) {
            int code = reader.read(width);
            if (code < 0 || code == END) {
                break;
            }
            if (code == CLEAR) {
                size = FIRST;
                width = 9;
                previous = null;
                continue;
            }

            byte[] entry;
            if (code < size) {
                entry = table[code];
            } else if (previous != null && code == size) {
                entry = append(previous, previous[0]);
            } else {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("code", code);
                details.put("size", size);
                throw new CodecError("the compressed data holds a code the table does not have", details);
            }

            if (out.overflows(entry.length)) {
                throw lengthError(
                        "the decompressed data is longer than the strip claims",
                        out.claimedLength(),
                        out.size() + entry.length);
            }
            out.add(entry, 0, entry.length);
            // a well behaved encoder clears before the table fills, and one that does not would
            // desync here rather than at some later strip
            if (previous != null && size < TABLE_LIMIT) {
                table[size] = append(previous, entry[0]);
                size += 1;
            }
            previous = entry;
            width = Math.min(MAX_WIDTH, widthFor(size + 1));
        }

        if (out.isClaimed() && out.size() != out.claimedLength()) {
            throw lengthError(
                    "the decompressed data is shorter than the strip claims", out.claimedLength(), out.size());
        }
        return out.toArray();
    }

    /** How many bits a code takes once the next code to be assigned is {@code next}. */
    private static int widthFor(int next) {
        if (next >= 2048) {
            return 12;
        }
        if (next >= 1024) {
            return 11;
        }
        if (next >= 512) {
            return 10;
        }
        return 9;
    }

    private static byte[] append(byte[] run, byte last) {
        byte[] out = Arrays.copyOf(run, run.length + 1);
        out[run.length] = last;
        return out;
    }

    private static CodecError lengthError(String message, int expected, int found) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("expected", expected);
        details.put("found", found);
        return new CodecError(message, details);
    }

    /** Reads codes most significant bit first. */
    private static final class BitReader {
        private final byte[] bytes;
        private final int end;
        private int at;
        private int bit;

        BitReader(byte[] bytes, int from, int to) {
            this.bytes = bytes;
            this.at = from;
            this.end = to;
        }

        /** The next code of the width, or minus one when the data runs out first. */
        int read(int width) {
            int value = 0;
            for (int i = 0; i < width; i += 1) {
                if (at >= end) {
                    return -1;
                }
                int next = (bytes[at] >> (7 - bit)) & 1;
                value = (value << 1) | next;
                bit += 1;
                if (bit == 8) {
                    bit = 0;
                    at += 1;
                }
            }
            return value;
        }
    }

    /** Writes codes most significant bit first, padding the last byte with zeros. */
    private static final class BitWriter {
        private final StripOutput out = StripOutput.unclaimed();
        private int current;
        private int bit;

        void write(int value, int width) {
            for (int i = width - 1; i >= 0; i -= 1) {
                current = (current << 1) | ((value >> i) & 1);
                bit += 1;
                if (bit == 8) {
                    out.add((byte) current);
                    current = 0;
                    bit = 0;
                }
            }
        }

        byte[] finish() {
            if (bit > 0) {
                out.add((byte) (current << (8 - bit)));
                current = 0;
                bit = 0;
            }
            return out.toArray();
        }
    }
}
