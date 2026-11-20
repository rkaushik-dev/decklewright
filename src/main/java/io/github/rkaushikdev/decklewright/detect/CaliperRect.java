package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Quad;
import java.util.Objects;

/**
 * The smallest rectangle, at any orientation, that holds a hull: what the rotating calipers find.
 *
 * @param quad the rectangle's corners, in the usual order
 * @param angle the direction of the hull edge the rectangle lies flush with, folded into the band
 *     from minus forty five up to forty five degrees
 * @param area the rectangle's area, in square pixels
 * @see QuadFit#minimumAreaRect(io.github.rkaushikdev.decklewright.geom.Polygon)
 */
public record CaliperRect(Quad quad, Angle angle, double area) {
    /** A rectangle of the given parts. */
    public CaliperRect {
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(angle, "angle");
    }
}
