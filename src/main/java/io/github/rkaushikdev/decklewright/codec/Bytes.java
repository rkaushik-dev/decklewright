package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Byte level helpers the codecs share.
 *
 * <p>All four of them had grown their own copy of read a big endian word, turn four bytes into a
 * type name, and glue a list of buffers together. They were the same code four times and one of
 * them had a different name for the same thing, so a reader had to check which file they were in
 * before trusting it.
 *
 * <p>The check value is the platform's {@link CRC32}. It is the same polynomial and the same
 * answer, it runs in native code, and it is one less table for anyone to transcribe wrongly.
 *
 * <p>Unsigned 32 bit words are carried as {@code long}s, so a word read back is never negative.
 */
public final class Bytes {
    /** The reversed crc32 polynomial, which the seeded check value is continued with. */
    private static final long POLYNOMIAL = 0xedb88320L;

    /** {@code x} to the power {@code 2^k}, modulo the polynomial, for {@code k} from zero to thirty one. */
    private static final long[] POWERS_OF_X = powersOfX();

    private static final long UINT32_MAX = 0xffffffffL;

    /** The longest array every Java virtual machine will allocate. */
    private static final int MAX_LENGTH = Integer.MAX_VALUE - 8;

    private Bytes() {}

    /** The crc32 check value of the bytes. */
    public static long crc32(byte[] bytes) {
        return crc32(bytes, 0, bytes.length, 0);
    }

    /**
     * The crc32 check value of the bytes, continued from the check value of whatever came before
     * them, so {@code crc32(cd, crc32(ab))} is {@code crc32(abcd)}.
     *
     * @param seed the check value so far, zero for a fresh start
     * @throws BadArgumentError when the seed is not an unsigned 32 bit value
     */
    public static long crc32(byte[] bytes, long seed) {
        return crc32(bytes, 0, bytes.length, seed);
    }

    /**
     * The crc32 check value of part of the bytes.
     *
     * @throws IndexOutOfBoundsException when the part is not inside the array
     */
    public static long crc32(byte[] bytes, int offset, int length) {
        return crc32(bytes, offset, length, 0);
    }

    /**
     * The crc32 check value of part of the bytes, continued from a seed.
     *
     * @param seed the check value so far, zero for a fresh start
     * @throws BadArgumentError when the seed is not an unsigned 32 bit value
     * @throws IndexOutOfBoundsException when the part is not inside the array
     */
    public static long crc32(byte[] bytes, int offset, int length, long seed) {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        Guards.requireInRange((double) seed, 0, UINT32_MAX, "seed");
        CRC32 check = new CRC32();
        check.update(bytes, offset, length);
        return seed == 0 ? check.getValue() : combine(seed, check.getValue(), length);
    }

    /** The unsigned big endian word of two bytes at {@code at}. */
    public static int readUint16BE(byte[] bytes, int at) {
        return ((bytes[at] & 0xff) << 8) | (bytes[at + 1] & 0xff);
    }

    /** The unsigned big endian word of four bytes at {@code at}. */
    public static long readUint32BE(byte[] bytes, int at) {
        return ((bytes[at] & 0xffL) << 24)
                | ((bytes[at + 1] & 0xff) << 16)
                | ((bytes[at + 2] & 0xff) << 8)
                | (bytes[at + 3] & 0xff);
    }

    /** Writes the low 32 bits of the value as four big endian bytes from {@code at}. */
    public static void writeUint32BE(byte[] bytes, int at, long value) {
        bytes[at] = (byte) (value >>> 24);
        bytes[at + 1] = (byte) (value >>> 16);
        bytes[at + 2] = (byte) (value >>> 8);
        bytes[at + 3] = (byte) value;
    }

    /** The low 32 bits of the value as a fresh four byte big endian word. */
    public static byte[] uint32BE(long value) {
        byte[] out = new byte[4];
        writeUint32BE(out, 0, value);
        return out;
    }

    /** A run of bytes read one character each. */
    public static String readAscii(byte[] bytes, int at, int length) {
        return new String(bytes, at, length, StandardCharsets.ISO_8859_1);
    }

    /**
     * A run of bytes read one character each, stopping at the first zero, which is how every string
     * in these formats ends.
     */
    public static String readAsciiZ(byte[] bytes, int at, int length) {
        int end = at;
        while (end < at + length && bytes[end] != 0) {
            end += 1;
        }
        return readAscii(bytes, at, end - at);
    }

    /** One byte per character, keeping the low eight bits of each. */
    public static byte[] asciiBytes(String text) {
        byte[] out = new byte[text.length()];
        for (int i = 0; i < text.length(); i += 1) {
            out[i] = (byte) text.charAt(i);
        }
        return out;
    }

    /**
     * One byte per character, refusing anything wider than a byte rather than truncating it.
     *
     * @throws CodecError naming the text when a character is outside latin one
     * @see #latin1Bytes(String, String)
     */
    public static byte[] latin1Bytes(String text) {
        return latin1Bytes(text, "that text");
    }

    /**
     * One byte per character, refusing anything wider than a byte rather than truncating it, because
     * a truncated identifier in a metadata chunk is worse than a failed write.
     *
     * @param what named in the error, such as {@code a png text key}
     * @throws CodecError naming the text when a character is outside latin one
     */
    public static byte[] latin1Bytes(String text, String what) {
        byte[] out = new byte[text.length()];
        for (int i = 0; i < text.length(); i += 1) {
            char code = text.charAt(i);
            if (code > 255) {
                throw new CodecError(what + " holds latin one and nothing wider", Map.of("text", text));
            }
            out[i] = (byte) code;
        }
        return out;
    }

    /**
     * A fresh buffer of zeros, for a length a codec worked out from a header.
     *
     * <p>A sixteen bit master of more than about a billion samples is more bytes than one Java array
     * holds, so its sample bytes cannot be laid out in one run. That is refused here, by name, rather
     * than surfacing as an allocation failure.
     *
     * @throws UnsupportedError when the length is more than one buffer can hold
     * @throws NegativeArraySizeException when the length is negative
     */
    public static byte[] allocate(long length) {
        if (length > MAX_LENGTH) {
            throw new UnsupportedError("that is more bytes than one buffer can hold", Map.of("bytes", length));
        }
        return new byte[(int) length];
    }

    /**
     * The parts glued together in order, in a new array.
     *
     * @throws UnsupportedError when together they are more than one buffer can hold
     */
    public static byte[] concatBytes(List<byte[]> parts) {
        long total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = allocate(total);
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /**
     * The check value of two runs end to end, from the check value of each and the length of the
     * second. This is zlib's {@code crc32_combine}: it moves the first check value past the second
     * run's length by multiplying it by {@code x^(8 * length)} modulo the polynomial.
     */
    private static long combine(long first, long second, long secondLength) {
        return multiplyModP(xToThe8nModP(secondLength), first) ^ second;
    }

    /** {@code x^(8n)} modulo the polynomial, built from the precomputed powers of two. */
    private static long xToThe8nModP(long n) {
        long product = 1L << 31; // x^0, in the reflected bit order crc32 uses
        int k = 3;
        for (long rest = n; rest != 0; rest >>>= 1, k += 1) {
            if ((rest & 1) != 0) {
                product = multiplyModP(POWERS_OF_X[k & 31], product);
            }
        }
        return product;
    }

    /** The product of two polynomials modulo the crc32 polynomial; {@code a} must not be zero. */
    private static long multiplyModP(long a, long b) {
        long mask = 1L << 31;
        long product = 0;
        long multiplicand = b;
        for (; ; ) {
            if ((a & mask) != 0) {
                product ^= multiplicand;
                if ((a & (mask - 1)) == 0) {
                    return product;
                }
            }
            mask >>>= 1;
            multiplicand = (multiplicand & 1) != 0 ? (multiplicand >>> 1) ^ POLYNOMIAL : multiplicand >>> 1;
        }
    }

    private static long[] powersOfX() {
        long[] powers = new long[32];
        long p = 1L << 30; // x^1
        powers[0] = p;
        for (int n = 1; n < 32; n += 1) {
            p = multiplyModP(p, p);
            powers[n] = p;
        }
        return powers;
    }
}
