package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Quad;
import java.util.Objects;

/**
 * The corners of a sheet after each was allowed to move onto the crossing of its two sides.
 *
 * @param quad the crossed corners, or the very quad handed in when the crossing was refused
 * @param shift how far the crossing would have moved each corner; zero all round when the sides
 *     could not be crossed at all
 * @param clamped true when the crossing was refused and the quad handed in was kept
 * @see Corners#refineCorners(Quad, SideLines)
 */
public record RefinedCorners(Quad quad, CornerShift shift, boolean clamped) {
    /** A result of the given parts. */
    public RefinedCorners {
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(shift, "shift");
    }
}
