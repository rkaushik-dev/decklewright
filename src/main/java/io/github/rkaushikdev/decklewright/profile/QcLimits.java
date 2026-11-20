package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Tolerance;
import java.util.Objects;

/**
 * What the quality checks take from a profile: the funder's numbers.
 *
 * @param sampling the sampling frequency a capture has to reach
 * @param samplingTolerance how far from it a capture may be
 * @param skewTolerance the most skew, in degrees, a cut may keep
 * @param illuminationTolerance the widest spread of brightness across the bed, as a share of its
 *     mean
 * @param grayBalanceTolerance the largest cast a neutral patch of the target may carry
 * @param minimumSnr the lowest signal to noise ratio a capture may have
 * @param maximumClipping the largest share of samples that may sit at either end of the range
 * @param minimumStars the grade, one to four stars, a capture has to reach to be delivered
 * @see Settings#qcLimits(Profile)
 */
public record QcLimits(
        Sampling sampling,
        Tolerance samplingTolerance,
        double skewTolerance,
        double illuminationTolerance,
        double grayBalanceTolerance,
        double minimumSnr,
        double maximumClipping,
        int minimumStars) {

    /** Limits of the given values. */
    public QcLimits {
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(samplingTolerance, "samplingTolerance");
    }
}
