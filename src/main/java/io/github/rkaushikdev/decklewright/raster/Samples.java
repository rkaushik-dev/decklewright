package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A run of unsigned samples at one depth: a surface's whole buffer, or a live window onto part of
 * one, such as a single row.
 *
 * <p>Eight bit samples are held one per byte in {@link Uint8Samples} and sixteen bit samples one per
 * {@code short} in {@link Uint16Samples}, so a sixteen bit master costs two bytes a sample and a
 * derivative one, which is what a sheet of a few hundred megapixels can afford.
 *
 * <p>A store truncates toward zero and then keeps only the bits the depth holds, which wraps the
 * value modulo two to the depth: three hundred stored at eight bits reads back as forty four, and
 * minus one as two hundred and fifty five. Not a number and the infinities store zero. A store
 * never saturates and never refuses a value.
 *
 * <p>{@link #subarray(int, int)} is a view that shares the samples; {@link #slice()} copies them.
 * Reading or writing outside the run throws rather than being ignored, so an indexing mistake fails
 * where it is made.
 */
public abstract sealed class Samples permits Uint8Samples, Uint16Samples {
    /** How many samples {@link #toString()} writes out before it stops. */
    private static final int SHOWN = 16;

    final int offset;
    final int length;

    Samples(int offset, int length) {
        this.offset = offset;
        this.length = length;
    }

    /**
     * A fresh run of zeros, stored the way a surface of this depth stores them.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     * @throws NegativeArraySizeException when the length is negative
     */
    public static Samples allocate(int depth, int length) {
        return Layouts.requireDepth(depth) == 16 ? Uint16Samples.allocate(length) : Uint8Samples.allocate(length);
    }

    /**
     * What a sample of the depth reads back after the value is stored into it, as {@link #set(int,
     * double)} stores it.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     */
    public static int storedValue(int depth, double value) {
        return Layouts.requireDepth(depth) == 16
                ? Uint16Samples.stored(value) & 0xffff
                : Uint8Samples.stored(value) & 0xff;
    }

    /** Bits per sample: eight or sixteen. */
    public abstract int depth();

    /** How many samples the run holds. */
    public final int length() {
        return length;
    }

    /**
     * The sample at the index, as an unsigned value.
     *
     * @throws IndexOutOfBoundsException when the index is outside the run
     */
    public abstract int get(int index);

    /**
     * Stores a whole number, keeping only the bits the depth holds.
     *
     * @throws IndexOutOfBoundsException when the index is outside the run
     */
    public abstract void set(int index, int value);

    /**
     * Stores a number truncated toward zero, then reduced to the bits the depth holds; not a number
     * and the infinities store zero.
     *
     * @throws IndexOutOfBoundsException when the index is outside the run
     */
    public abstract void set(int index, double value);

    /**
     * Copies every sample of the source into this run from its start.
     *
     * @throws IndexOutOfBoundsException when the source is longer than this run
     */
    public final void set(Samples source) {
        set(source, 0);
    }

    /**
     * Copies every sample of the source into this run from {@code start} on, converting the value
     * of each when the depths differ. The source may overlap this run.
     *
     * @throws IndexOutOfBoundsException when the source does not fit from that position
     */
    public abstract void set(Samples source, int start);

    /** Stores the value, converted as {@link #set(int, double)} converts it, into every sample. */
    public abstract void fill(double value);

    /**
     * A live window onto the samples from {@code start} up to but not including {@code end}.
     * Writing through it writes this run.
     *
     * @throws IndexOutOfBoundsException when the range is not inside the run
     */
    public abstract Samples subarray(int start, int end);

    /** A copy of the run in a buffer of its own. */
    public abstract Samples slice();

    /** The samples as unsigned values, in a new array. */
    public final int[] toArray() {
        int[] out = new int[length];
        for (int i = 0; i < length; i += 1) {
            out[i] = get(i);
        }
        return out;
    }

    /** The class, then the samples, such as {@code Uint8Samples[1, 2, 3]}; a long run is cut short. */
    @Override
    public final String toString() {
        StringJoiner out = new StringJoiner(", ", getClass().getSimpleName() + "[", "]");
        int shown = Math.min(length, SHOWN);
        for (int i = 0; i < shown; i += 1) {
            out.add(Integer.toString(get(i)));
        }
        if (length > shown) {
            out.add("... " + (length - shown) + " more");
        }
        return out.toString();
    }

    /** Where the sample at {@code index} sits in the backing array, after checking it is in the run. */
    final int slot(int index) {
        return offset + Objects.checkIndex(index, length);
    }

    /** Where a run of {@code size} samples from {@code index} starts in the backing array, after checking it fits. */
    final int slots(int index, int size) {
        return offset + Objects.checkFromIndexSize(index, size, length);
    }
}
