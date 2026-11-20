package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Objects;

/**
 * What {@link Crops#cropPadded} cut, and how much of it had to be made up.
 *
 * @param surface the crop, at the size the plan asked for
 * @param filled true when any of it fell off the capture and was filled
 * @param covered the part that came from the capture, in the capture's coordinates; empty when
 *     none did
 */
public record PaddedCrop(Surface surface, boolean filled, Rect covered) {
    /** A crop of the given parts. */
    public PaddedCrop {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(covered, "covered");
    }
}
