package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.raster.Fill;
import java.util.Objects;

/**
 * How a pad fills the border it adds.
 *
 * @param mode what goes in the border
 * @param fill the tone of a {@linkplain PadMode#CONSTANT constant} border; one value per channel, or
 *     one for all of them
 */
public record PadOptions(PadMode mode, Fill fill) {
    /** Options of the given parts. */
    public PadOptions {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(fill, "fill");
    }

    /** A constant black border. */
    public static PadOptions defaults() {
        return new PadOptions(PadMode.CONSTANT, Fill.uniform(0));
    }

    /** These options with another mode. */
    public PadOptions withMode(PadMode mode) {
        return new PadOptions(mode, fill);
    }

    /** These options with another fill. */
    public PadOptions withFill(Fill fill) {
        return new PadOptions(mode, fill);
    }

    /** These options with the same fill value in every channel. */
    public PadOptions withFill(double value) {
        return new PadOptions(mode, Fill.uniform(value));
    }
}
