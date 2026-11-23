package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Tolerance;
import java.util.List;
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
 * @param toneAims what each patch of the step wedge should read, in sample values; copied, and
 *     empty when the profile does not aim its wedge
 * @param toneTolerance how far off its aim any one patch may read, in sample values
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
        int minimumStars,
        List<Double> toneAims,
        double toneTolerance) {

    /** Limits of the given values; the tone aims are copied. */
    public QcLimits {
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(samplingTolerance, "samplingTolerance");
        toneAims = List.copyOf(toneAims);
    }

    /** These limits with another skew tolerance, in degrees. */
    public QcLimits withSkewTolerance(double degrees) {
        return new QcLimits(
                sampling,
                samplingTolerance,
                degrees,
                illuminationTolerance,
                grayBalanceTolerance,
                minimumSnr,
                maximumClipping,
                minimumStars,
                toneAims,
                toneTolerance);
    }

    /** These limits with another illumination tolerance, as a share of the bed's mean. */
    public QcLimits withIlluminationTolerance(double share) {
        return new QcLimits(
                sampling,
                samplingTolerance,
                skewTolerance,
                share,
                grayBalanceTolerance,
                minimumSnr,
                maximumClipping,
                minimumStars,
                toneAims,
                toneTolerance);
    }

    /** These limits with another gray balance tolerance, in sample values. */
    public QcLimits withGrayBalanceTolerance(double values) {
        return new QcLimits(
                sampling,
                samplingTolerance,
                skewTolerance,
                illuminationTolerance,
                values,
                minimumSnr,
                maximumClipping,
                minimumStars,
                toneAims,
                toneTolerance);
    }

    /** These limits with another noise floor and clipping ceiling. */
    public QcLimits withNoise(double snr, double clipping) {
        return new QcLimits(
                sampling,
                samplingTolerance,
                skewTolerance,
                illuminationTolerance,
                grayBalanceTolerance,
                snr,
                clipping,
                minimumStars,
                toneAims,
                toneTolerance);
    }

    /** These limits aiming the wedge's patches at the given values, held to the given tolerance. */
    public QcLimits withTone(List<Double> aims, double tolerance) {
        return new QcLimits(
                sampling,
                samplingTolerance,
                skewTolerance,
                illuminationTolerance,
                grayBalanceTolerance,
                minimumSnr,
                maximumClipping,
                minimumStars,
                aims,
                tolerance);
    }
}
