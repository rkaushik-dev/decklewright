package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.starsFor;

import io.github.rkaushikdev.decklewright.measure.Axis;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.QcLimits;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Sampling frequency and skew: the two checks that read a number somebody else already measured.
 *
 * <p>Neither of them touches a pixel. The frequency comes off the scale bar and the skew off the
 * detector, and both are already in the capture reading. They are checks rather than readings
 * because the profile has an opinion about what an acceptable value is and the detector does not.
 */
public final class SamplingAndSkew {
    /**
     * How far off the stated frequency a capture may be, as a share of it, per star: half a percent
     * for four stars.
     */
    public static final List<Rung> SAMPLING_LADDER =
            List.of(new Rung(4, 0.005), new Rung(3, 0.01), new Rung(2, 0.02), new Rung(1, 0.05));

    /** How far off square a sheet may be, in degrees, per star. */
    public static final List<Rung> SKEW_LADDER =
            List.of(new Rung(4, 0.1), new Rung(3, 0.3), new Rung(2, 0.6), new Rung(1, 1.5));

    /** How far apart the three skew measures may be, in degrees, per star. */
    private static final List<Rung> AGREEMENT_LADDER =
            List.of(new Rung(4, 0.2), new Rung(3, 0.5), new Rung(2, 1), new Rung(1, 2));

    private SamplingAndSkew() {}

    /**
     * The frequency against the one the profile states. It is symmetric, since too fine is as wrong
     * as too coarse against a target, and a capture outside the profile's own tolerance gets no stars
     * whatever the ladder says. Both axes are held to it and the one furthest off is graded, so the
     * measurement is the frequency on that axis, in ppi, and the note says which axis it was when the
     * capture is not sampled alike both ways.
     */
    public static Check samplingCheck() {
        return Check.of("sampling", List.of(Need.SAMPLING, Need.LIMITS), SamplingAndSkew::measureSampling);
    }

    /**
     * A capture below the stated frequency is worse than one above it, because detail that was never
     * sampled cannot be got back and detail sampled too finely only costs disk. The ladder is one
     * sided for that reason. The shorter axis is the one graded, and the measurement is its frequency,
     * in ppi.
     */
    public static Check samplingFloorCheck() {
        return Check.of("samplingFloor", List.of(Need.SAMPLING, Need.LIMITS), SamplingAndSkew::measureFloor);
    }

    /**
     * How far off square the sheet is, whichever way it turns, held to the profile's skew tolerance.
     * The measurement is in degrees.
     */
    public static Check skewCheck() {
        return Check.of("skew", List.of(Need.SKEW, Need.LIMITS), SamplingAndSkew::measureSkew);
    }

    /**
     * How far apart the three skew measures were. A capture the detector was sure about is worth
     * more than one it guessed at, and this is the only check that grades the measurement rather
     * than the capture. The measurement is in degrees.
     */
    public static Check skewAgreementCheck() {
        return Check.of("skewAgreement", List.of(Need.SPREAD), SamplingAndSkew::measureAgreement);
    }

    private static Measurement measureSampling(QcContext context) {
        QcLimits limits = context.limits().orElseThrow();
        Sampling got = context.sampling().orElseThrow();
        AxisReading worst = worstAxis(got, limits.sampling(), AxisReading::off);

        boolean withinProfile = Stream.of(Axis.values()).allMatch(axis -> limits.samplingTolerance()
                .contains(limits.sampling().along(axis), got.along(axis)));
        int stars = withinProfile ? starsFor(worst.off(), SAMPLING_LADDER) : 0;

        return new Measurement(
                worst.got(),
                stars,
                NumberText.of(tenths(worst.got())) + " ppi" + worst.where(got) + " against "
                        + NumberText.of(tenths(worst.wanted())) + ", off by " + NumberText.fixed(worst.off() * 100, 2)
                        + " percent");
    }

    private static Measurement measureFloor(QcContext context) {
        Sampling got = context.sampling().orElseThrow();
        AxisReading worst = worstAxis(got, context.limits().orElseThrow().sampling(), AxisReading::shortBy);
        double shortBy = worst.shortBy();
        String reading = NumberText.of(tenths(worst.got())) + " ppi" + worst.where(got);
        String note = shortBy == 0
                ? reading + ", at or above the " + NumberText.of(tenths(worst.wanted())) + " asked for"
                : reading + ", " + NumberText.fixed(shortBy * 100, 2) + " percent short of "
                        + NumberText.of(tenths(worst.wanted()));
        return new Measurement(worst.got(), starsFor(shortBy, SAMPLING_LADDER), note);
    }

    /**
     * The axis that reads worst by the given measure, across before down on a tie. A scanner whose
     * stepper motor slips samples down the bed coarser than across it, and a check that only read
     * across would pass every one of those captures.
     */
    private static AxisReading worstAxis(Sampling got, Sampling wanted, ToDoubleFunction<AxisReading> badness) {
        AxisReading across = new AxisReading(Axis.X, got.x(), wanted.x());
        AxisReading down = new AxisReading(Axis.Y, got.y(), wanted.y());
        return badness.applyAsDouble(down) > badness.applyAsDouble(across) ? down : across;
    }

    /** One axis of a capture's frequency against the one asked for on that axis. */
    private record AxisReading(Axis axis, double got, double wanted) {
        /** How far off, either way, as a share of the frequency asked for. */
        double off() {
            return Math.abs(got - wanted) / wanted;
        }

        /** How far short, as a share of the frequency asked for; zero at or above it. */
        double shortBy() {
            return Math.max(0, (wanted - got) / wanted);
        }

        /** Nothing for a square capture, else which way along the bed this axis runs. */
        String where(Sampling sampling) {
            if (sampling.isSquare()) {
                return "";
            }
            return axis == Axis.X ? " across" : " down";
        }
    }

    private static Measurement measureSkew(QcContext context) {
        double degrees = Math.abs(context.skew().orElseThrow().degrees());
        double allowed = context.limits().orElseThrow().skewTolerance();
        int stars = degrees <= allowed ? starsFor(degrees, SKEW_LADDER) : 0;
        return new Measurement(
                degrees,
                stars,
                NumberText.fixed(degrees, 3) + " deg off square against a limit of " + NumberText.of(allowed));
    }

    private static Measurement measureAgreement(QcContext context) {
        double spread = context.spread().orElseThrow();
        return new Measurement(
                spread,
                starsFor(spread, AGREEMENT_LADDER),
                "the three skew measures spread over " + NumberText.fixed(spread, 3) + " deg");
    }

    /** The frequency to a tenth of a ppi, halves up, as a note prints it. */
    private static double tenths(double value) {
        return Num.roundHalfUp(value * 10) / 10;
    }
}
