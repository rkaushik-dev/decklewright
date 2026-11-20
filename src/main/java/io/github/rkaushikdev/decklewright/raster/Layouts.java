package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.List;
import java.util.Objects;

/**
 * Channel layouts and sample depths.
 *
 * <p>A depth is the number of bits in one sample, and only two exist in the lab: sixteen for
 * masters and eight for everything handed out. Depths stay plain {@code int}s, because every file
 * header and profile states them as numbers and the arithmetic on them is the point.
 *
 * @see Layout
 */
public final class Layouts {
    /** Every layout, in the order the lab lists them. */
    public static final List<Layout> LAYOUTS = List.of(Layout.values());

    /** Every sample depth, in bits. */
    public static final List<Integer> DEPTHS = List.of(8, 16);

    private Layouts() {}

    /**
     * The depth, when it is one the lab works in.
     *
     * @throws BadArgumentError when it is neither eight nor sixteen
     */
    public static int requireDepth(int depth) {
        return Guards.requireOneOf(depth, DEPTHS, "depth");
    }

    /**
     * The largest value a sample can hold at this depth.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     */
    public static int maxValue(int depth) {
        return requireDepth(depth) == 16 ? 65535 : 255;
    }

    /**
     * The sample storage a surface of this depth keeps its samples in.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     */
    public static Class<? extends Samples> arrayTypeFor(int depth) {
        return requireDepth(depth) == 16 ? Uint16Samples.class : Uint8Samples.class;
    }

    /**
     * How many bytes one row of samples occupies.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     */
    public static long rowBytes(int width, Layout layout, int depth) {
        Objects.requireNonNull(layout, "layout");
        return (long) width * layout.channelCount() * (requireDepth(depth) / 8);
    }
}
