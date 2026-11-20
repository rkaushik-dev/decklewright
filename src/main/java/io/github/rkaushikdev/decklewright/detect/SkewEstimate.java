package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import java.util.Objects;

/**
 * One stage of the projection search for skew: the best angle it found and what that cost.
 *
 * @param angle the angle whose projection scored best
 * @param score how sharp the projection was at that angle; higher is sharper
 * @param tried how many angles were scored
 * @see Deskew#coarseSkew(Mask)
 * @see Deskew#refineSkew(Mask, Angle)
 */
public record SkewEstimate(Angle angle, double score, int tried) {
    /** An estimate of the given parts. */
    public SkewEstimate {
        Objects.requireNonNull(angle, "angle");
    }
}
