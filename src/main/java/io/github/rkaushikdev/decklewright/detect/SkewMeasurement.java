package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import java.util.Objects;

/**
 * The skew of a mask measured by projection: a coarse sweep, then a refinement around its answer.
 *
 * @param angle the refined skew
 * @param coarse the best candidate the sweep found, which the refinement started from
 * @param score how sharp the projection was at the refined angle
 * @param tried how many angles the sweep and the refinement scored between them
 * @see Deskew#measureSkew(Mask)
 */
public record SkewMeasurement(Angle angle, Angle coarse, double score, int tried) {
    /** A measurement of the given parts. */
    public SkewMeasurement {
        Objects.requireNonNull(angle, "angle");
        Objects.requireNonNull(coarse, "coarse");
    }
}
