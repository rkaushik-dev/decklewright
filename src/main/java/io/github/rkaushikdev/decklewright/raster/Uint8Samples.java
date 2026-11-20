package io.github.rkaushikdev.decklewright.raster;

import java.util.Arrays;
import java.util.Objects;

/**
 * Eight bit samples, one per byte, read back as values from zero to two hundred and fifty five.
 *
 * <p>This is the storage of every eight bit surface, and the type a codec wraps its decoded bytes
 * in to copy them into a row with {@link Samples#set(Samples)}.
 */
public final class Uint8Samples extends Samples {
    private final byte[] array;

    private Uint8Samples(byte[] array, int offset, int length) {
        super(offset, length);
        this.array = array;
    }

    /**
     * A fresh run of zeros.
     *
     * @throws NegativeArraySizeException when the length is negative
     */
    public static Uint8Samples allocate(int length) {
        return new Uint8Samples(new byte[length], 0, length);
    }

    /** A live run over the whole array; writing either one writes the other. */
    public static Uint8Samples wrap(byte[] array) {
        Objects.requireNonNull(array, "array");
        return new Uint8Samples(array, 0, array.length);
    }

    /**
     * A live run over part of the array.
     *
     * @throws IndexOutOfBoundsException when the part is not inside the array
     */
    public static Uint8Samples wrap(byte[] array, int offset, int length) {
        Objects.requireNonNull(array, "array");
        Objects.checkFromIndexSize(offset, length, array.length);
        return new Uint8Samples(array, offset, length);
    }

    @Override
    public int depth() {
        return 8;
    }

    @Override
    public int get(int index) {
        return array[slot(index)] & 0xff;
    }

    @Override
    public void set(int index, int value) {
        array[slot(index)] = (byte) value;
    }

    @Override
    public void set(int index, double value) {
        array[slot(index)] = stored(value);
    }

    @Override
    public void set(Samples source, int start) {
        int first = slots(start, source.length());
        switch (source) {
            case Uint8Samples bytes -> System.arraycopy(bytes.array, bytes.offset, array, first, bytes.length);
            case Uint16Samples shorts -> {
                for (int i = 0; i < shorts.length(); i += 1) {
                    array[first + i] = (byte) shorts.get(i);
                }
            }
        }
    }

    @Override
    public void fill(double value) {
        Arrays.fill(array, offset, offset + length, stored(value));
    }

    @Override
    public Uint8Samples subarray(int start, int end) {
        Objects.checkFromToIndex(start, end, length);
        return new Uint8Samples(array, offset + start, end - start);
    }

    @Override
    public Uint8Samples slice() {
        return new Uint8Samples(Arrays.copyOfRange(array, offset, offset + length), 0, length);
    }

    /**
     * The byte a sample holds after the value is stored: truncated toward zero, then wrapped
     * modulo two hundred and fifty six, with not a number and the infinities storing zero. The
     * remainder is exact and keeps the sign, so truncating it agrees with truncating the value
     * itself on the low eight bits, and it is not a number for the infinities, which casts to zero
     * as not a number does.
     */
    static byte stored(double value) {
        return (byte) (int) (value % 256);
    }
}
