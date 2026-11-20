package io.github.rkaushikdev.decklewright.codec.png;

import java.util.Arrays;

/**
 * The row filter the heuristic picked and the row filtered with it.
 *
 * @param filter the filter number, one of the {@code FILTER_} constants in {@link Filters}
 * @param data the filtered row, in an array of its own that the caller may keep and change
 */
public record ChosenFilter(int filter, byte[] data) {
    @Override
    public boolean equals(Object other) {
        return other instanceof ChosenFilter that && filter == that.filter && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * filter + Arrays.hashCode(data);
    }

    /** The filter and the row, such as {@code ChosenFilter[filter=2, data=[0, 0, 0]]}. */
    @Override
    public String toString() {
        return "ChosenFilter[filter=" + filter + ", data=" + Arrays.toString(data) + "]";
    }
}
