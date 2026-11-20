package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Polygon;
import io.github.rkaushikdev.decklewright.geom.Quad;
import java.util.Objects;

/**
 * The rectangle that holds a sheet, fitted to the whole outline of its mask.
 *
 * @param quad the minimum area rectangle over the outline's hull
 * @param angle the skew of the rectangle's longer pair of sides, folded into the band from minus
 *     forty five up to forty five degrees
 * @param hull the convex hull of the outline
 * @param fill the share of the rectangle the mask covers, which is near one for a sheet and low
 *     for anything that is not a rectangle
 * @see QuadFit#fitQuad(Mask)
 */
public record FittedQuad(Quad quad, Angle angle, Polygon hull, double fill) {
    /** A fit of the given parts. */
    public FittedQuad {
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(angle, "angle");
        Objects.requireNonNull(hull, "hull");
    }
}
