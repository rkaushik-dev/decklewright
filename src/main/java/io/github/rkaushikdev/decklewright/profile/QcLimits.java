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
 * @param registrationTolerance how far apart the colour channels may put an edge, in pixels
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
        double toneTolerance,
        double registrationTolerance) {

    /** Limits of the given values; the tone aims are copied. */
    public QcLimits {
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(samplingTolerance, "samplingTolerance");
        toneAims = List.copyOf(toneAims);
    }

    /** These limits with another skew tolerance, in degrees. */
    public QcLimits withSkewTolerance(double degrees) {
        Draft draft = new Draft(this);
        draft.skewTolerance = degrees;
        return draft.build();
    }

    /** These limits with another illumination tolerance, as a share of the bed's mean. */
    public QcLimits withIlluminationTolerance(double share) {
        Draft draft = new Draft(this);
        draft.illuminationTolerance = share;
        return draft.build();
    }

    /** These limits with another gray balance tolerance, in sample values. */
    public QcLimits withGrayBalanceTolerance(double values) {
        Draft draft = new Draft(this);
        draft.grayBalanceTolerance = values;
        return draft.build();
    }

    /** These limits with another noise floor and clipping ceiling. */
    public QcLimits withNoise(double snr, double clipping) {
        Draft draft = new Draft(this);
        draft.minimumSnr = snr;
        draft.maximumClipping = clipping;
        return draft.build();
    }

    /** These limits aiming the wedge's patches at the given values, held to the given tolerance. */
    public QcLimits withTone(List<Double> aims, double tolerance) {
        Draft draft = new Draft(this);
        draft.toneAims = aims;
        draft.toneTolerance = tolerance;
        return draft.build();
    }

    /** These limits letting the colour channels sit another distance apart, in pixels. */
    public QcLimits withRegistrationTolerance(double pixels) {
        Draft draft = new Draft(this);
        draft.registrationTolerance = pixels;
        return draft.build();
    }

    /** Limits being changed a figure at a time, so no change has to spell out the rest. */
    private static final class Draft {
        private final Sampling sampling;
        private final Tolerance samplingTolerance;
        private double skewTolerance;
        private double illuminationTolerance;
        private double grayBalanceTolerance;
        private double minimumSnr;
        private double maximumClipping;
        private final int minimumStars;
        private List<Double> toneAims;
        private double toneTolerance;
        private double registrationTolerance;

        Draft(QcLimits from) {
            sampling = from.sampling;
            samplingTolerance = from.samplingTolerance;
            skewTolerance = from.skewTolerance;
            illuminationTolerance = from.illuminationTolerance;
            grayBalanceTolerance = from.grayBalanceTolerance;
            minimumSnr = from.minimumSnr;
            maximumClipping = from.maximumClipping;
            minimumStars = from.minimumStars;
            toneAims = from.toneAims;
            toneTolerance = from.toneTolerance;
            registrationTolerance = from.registrationTolerance;
        }

        QcLimits build() {
            return new QcLimits(
                    sampling,
                    samplingTolerance,
                    skewTolerance,
                    illuminationTolerance,
                    grayBalanceTolerance,
                    minimumSnr,
                    maximumClipping,
                    minimumStars,
                    toneAims,
                    toneTolerance,
                    registrationTolerance);
        }
    }
}
