package io.github.rkaushikdev.decklewright.raster;

import java.util.Arrays;
import java.util.Objects;

/**
 * Sixteen bit samples, one per {@code short}, read back as values from zero to sixty five thousand
 * five hundred and thirty five.
 *
 * <p>This is the storage of every sixteen bit surface, which is to say every master.
 */
public final class Uint16Samples extends Samples {
    private final short[] array;

    private Uint16Samples(short[] array, int offset, int length) {
        super(offset, length);
        this.array = array;
    }

    /**
     * A fresh run of zeros.
     *
     * @throws NegativeArraySizeException when the length is negative
     */
    public static Uint16Samples allocate(int length) {
        return new Uint16Samples(new short[length], 0, length);
    }

    /** A live run over the whole array; writing either one writes the other. */
    public static Uint16Samples wrap(short[] array) {
        Objects.requireNonNull(array, "array");
        return new Uint16Samples(array, 0, array.length);
    }

    /**
     * A live run over part of the array.
     *
     * @throws IndexOutOfBoundsException when the part is not inside the array
     */
    public static Uint16Samples wrap(short[] array, int offset, int length) {
        Objects.requireNonNull(array, "array");
        Objects.checkFromIndexSize(offset, length, array.length);
        return new Uint16Samples(array, offset, length);
    }

    @Override
    public int depth() {
        return 16;
    }

    @Override
    public int get(int index) {
        return array[slot(index)] & 0xffff;
    }

    @Override
    public void set(int index, int value) {
        array[slot(index)] = (short) value;
    }

    @Override
    public void set(int index, double value) {
        array[slot(index)] = stored(value);
    }

    @Override
    public void set(Samples source, int start) {
        int first = slots(start, source.length());
        switch (source) {
            case Uint16Samples shorts -> System.arraycopy(shorts.array, shorts.offset, array, first, shorts.length);
            case Uint8Samples bytes -> {
                for (int i = 0; i < bytes.length(); i += 1) {
                    array[first + i] = (short) bytes.get(i);
                }
            }
        }
    }

    @Override
    public void fill(double value) {
        Arrays.fill(array, offset, offset + length, stored(value));
    }

    @Override
    public Uint16Samples subarray(int start, int end) {
        Objects.checkFromToIndex(start, end, length);
        return new Uint16Samples(array, offset + start, end - start);
    }

    @Override
    public Uint16Samples slice() {
        return new Uint16Samples(Arrays.copyOfRange(array, offset, offset + length), 0, length);
    }

    /**
     * The value a {@code Uint16Array} would hold after storing the number. The remainder is exact
     * and keeps the sign, so truncating it agrees with truncating the value itself on the low
     * sixteen bits, and it is not a number for the infinities, which casts to zero as not a number
     * does.
     */
    static short stored(double value) {
        return (short) (int) (value % 65536);
    }
}
