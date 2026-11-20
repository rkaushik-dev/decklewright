package io.github.rkaushikdev.decklewright.codec.tiff;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PackBits, the run length scheme baseline tiff uses.
 *
 * <p>One control byte then either a literal run or a repeat. It compresses a scanned map sheet by
 * about a third, because the paper between the linework is a long flat run, and it costs nothing to
 * decode. The flatbed writes it by default and the vendor gives no way to turn it off.
 */
public final class PackBits {
    /** The control byte that stands for nothing at all. */
    private static final int NO_OPERATION = 128;

    /** The longest run one control byte can describe. */
    private static final int MAX_RUN = 128;

    private PackBits() {}

    /**
     * The bytes unpacked, however many that comes to.
     *
     * @throws CodecError when a literal run reaches past the end of the data, or a repeat run has no
     *     byte to repeat
     */
    public static byte[] unpackBits(byte[] bytes) {
        return unpack(bytes, 0, bytes.length, StripOutput.unclaimed());
    }

    /**
     * The bytes unpacked, checked against how many the caller wants back.
     *
     * @throws CodecError when the data is malformed, or unpacks to more or fewer bytes than expected
     * @throws NegativeArraySizeException when the expected length is negative
     */
    public static byte[] unpackBits(byte[] bytes, int expected) {
        return unpack(bytes, 0, bytes.length, StripOutput.claimed(new byte[expected]));
    }

    /**
     * The bytes from {@code from} up to {@code to} unpacked straight into {@code into}, which must
     * come out exactly full, so a strip is read without copying it out of the file first.
     *
     * @return {@code into}
     * @throws CodecError when the data is malformed, or unpacks to more or fewer bytes than {@code
     *     into} holds
     */
    static byte[] unpackBits(byte[] bytes, int from, int to, byte[] into) {
        return unpack(bytes, from, to, StripOutput.claimed(into));
    }

    /**
     * Pack a run of bytes. Repeats of three or more become a repeat run and everything else goes out
     * literally, which is what the specification recommends and what every reader is tested against.
     */
    public static byte[] packBits(byte[] bytes) {
        StripOutput out = StripOutput.unclaimed();
        int at = 0;
        while (at < bytes.length) {
            int runLength = 1;
            while (at + runLength < bytes.length && runLength < MAX_RUN && bytes[at + runLength] == bytes[at]) {
                runLength += 1;
            }

            if (runLength >= 3) {
                out.add((byte) (257 - runLength));
                out.add(bytes[at]);
                at += runLength;
                continue;
            }

            int literal = 0;
            while (at + literal < bytes.length && literal < MAX_RUN) {
                int remaining = bytes.length - (at + literal);
                if (remaining >= 3
                        && bytes[at + literal] == bytes[at + literal + 1]
                        && bytes[at + literal] == bytes[at + literal + 2]) {
                    break;
                }
                literal += 1;
            }
            out.add((byte) (literal - 1));
            out.add(bytes, at, literal);
            at += literal;
        }
        return out.toArray();
    }

    /**
     * How many bytes a packed run would take, without keeping it. The strip writer needs this to lay
     * out the byte counts before it commits.
     */
    public static int packedLength(byte[] bytes) {
        return packBits(bytes).length;
    }

    private static byte[] unpack(byte[] bytes, int from, int to, StripOutput out) {
        int at = from;
        while (at < to) {
            int control = bytes[at] & 0xff;
            at += 1;
            if (control == NO_OPERATION) {
                continue;
            }
            if (control < NO_OPERATION) {
                int count = control + 1;
                if (at + count > to) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("at", at - from);
                    details.put("count", count);
                    throw new CodecError("a literal run reaches past the end of the data", details);
                }
                requireRoom(out, count);
                out.add(bytes, at, count);
                at += count;
            } else {
                int count = 257 - control;
                if (at >= to) {
                    throw new CodecError("a repeat run has no byte to repeat", Map.of("at", at - from));
                }
                byte value = bytes[at];
                at += 1;
                requireRoom(out, count);
                out.repeat(value, count);
            }
        }
        if (out.isClaimed() && out.size() != out.claimedLength()) {
            throw lengthError("the unpacked data is shorter than the strip claims", out.claimedLength(), out.size());
        }
        return out.toArray();
    }

    private static void requireRoom(StripOutput out, int count) {
        if (out.overflows(count)) {
            throw lengthError(
                    "the unpacked data is longer than the strip claims", out.claimedLength(), out.size() + count);
        }
    }

    private static CodecError lengthError(String message, int expected, int found) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("expected", expected);
        details.put("found", found);
        return new CodecError(message, details);
    }
}
