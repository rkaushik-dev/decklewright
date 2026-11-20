package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.raster.Fill;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * How a set is derived from a plan.
 *
 * @param level the zlib level, where a format has one; the codec's own default when empty
 * @param fill what the corners a straightening uncovers, and any part of the crop past the edge of
 *     the capture, are filled with
 * @see Derivatives#deriveFromPlan
 */
public record DeriveOptions(OptionalInt level, Fill fill) {
    /** Options of the given parts. */
    public DeriveOptions {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(fill, "fill");
    }

    /** The codec's own level, and black fill. */
    public static DeriveOptions defaults() {
        return new DeriveOptions(OptionalInt.empty(), Fill.uniform(0));
    }

    /** These options with a compression level. */
    public DeriveOptions withLevel(int level) {
        return new DeriveOptions(OptionalInt.of(level), fill);
    }

    /** These options with another fill. */
    public DeriveOptions withFill(Fill fill) {
        return new DeriveOptions(level, fill);
    }
}
