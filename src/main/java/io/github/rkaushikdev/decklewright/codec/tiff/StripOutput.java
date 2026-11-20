package io.github.rkaushikdev.decklewright.codec.tiff;

import io.github.rkaushikdev.decklewright.DeckleError;
import java.util.Arrays;

/**
 * Where a strip decoder puts what it decodes.
 *
 * <p>When the caller knows how long the strip should come out, the bytes go straight into a buffer of
 * exactly that length, and the decoder asks {@link #overflows(int)} before each run so a strip that
 * runs long is refused rather than written past the end. When nothing is claimed the buffer grows.
 */
final class StripOutput {
    private static final int INITIAL = 256;

    private byte[] buffer;
    private int size;
    private final boolean claimed;

    private StripOutput(byte[] buffer, boolean claimed) {
        this.buffer = buffer;
        this.claimed = claimed;
    }

    /** Output into the buffer, whose length is what the strip claims to decode to. */
    static StripOutput claimed(byte[] buffer) {
        return new StripOutput(buffer, true);
    }

    /** Output that grows as it is written. */
    static StripOutput unclaimed() {
        return new StripOutput(new byte[INITIAL], false);
    }

    /** True when the output has a claimed length. */
    boolean isClaimed() {
        return claimed;
    }

    /** The claimed length; only meaningful when {@link #isClaimed()}. */
    int claimedLength() {
        return buffer.length;
    }

    /** How many bytes have been written. */
    int size() {
        return size;
    }

    /** True when writing {@code count} more bytes would go past the claimed length. */
    boolean overflows(int count) {
        return claimed && (long) size + count > buffer.length;
    }

    /** Writes one byte. */
    void add(byte value) {
        reserve(1);
        buffer[size] = value;
        size += 1;
    }

    /** Writes {@code count} bytes of the source from {@code from}. */
    void add(byte[] source, int from, int count) {
        reserve(count);
        System.arraycopy(source, from, buffer, size, count);
        size += count;
    }

    /** Writes the byte {@code count} times. */
    void repeat(byte value, int count) {
        reserve(count);
        Arrays.fill(buffer, size, size + count, value);
        size += count;
    }

    /** What was written, in an array of exactly that length. */
    byte[] toArray() {
        return size == buffer.length ? buffer : Arrays.copyOf(buffer, size);
    }

    private void reserve(int count) {
        if (size + count <= buffer.length) {
            return;
        }
        if (claimed) {
            throw new DeckleError("a decoder wrote past the claimed length without asking");
        }
        buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + count));
    }
}
