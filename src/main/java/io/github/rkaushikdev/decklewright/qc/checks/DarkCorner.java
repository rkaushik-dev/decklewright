package io.github.rkaushikdev.decklewright.qc.checks;

import java.util.Objects;

/**
 * The darkest corner of a region, which is what an operator needs in order to move a lamp.
 *
 * @param corner {@code top left}, {@code top right}, {@code bottom left} or {@code bottom right}
 * @param mean the mean of the first channel there; not a number when no corner could be read
 * @see Illumination#darkestCorner(io.github.rkaushikdev.decklewright.raster.Surface,
 *     io.github.rkaushikdev.decklewright.geom.Rect)
 */
public record DarkCorner(String corner, double mean) {
    /** A reading of the given parts. */
    public DarkCorner {
        Objects.requireNonNull(corner, "corner");
    }
}
