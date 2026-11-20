package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An angle stored in degrees, because every profile, work order and quality report in the lab is
 * written in degrees and converting at the edges kept losing a sign somewhere in the middle.
 *
 * <p>Positive turns clockwise on screen, matching {@link Point#rotatedAround(Point, double)}.
 * {@link #equals(Object)} compares the stored degrees exactly, so 10 and 370 are different records;
 * {@link #approxEquals(Angle, double)} treats angles a whole number of turns apart as the same.
 *
 * @param degrees the angle in degrees, not folded into any range
 */
public record Angle(double degrees) implements JsonValue {

    /**
     * An angle of the given degrees.
     *
     * @throws BadArgumentError when the degrees are not finite
     */
    public Angle {
        Guards.requireFinite(degrees, "degrees");
    }

    /**
     * An angle of the given degrees.
     *
     * @throws BadArgumentError when the value is not finite
     */
    public static Angle degrees(double value) {
        return new Angle(value);
    }

    /**
     * An angle of the given radians, stored as degrees.
     *
     * @throws BadArgumentError when the value is not finite
     */
    public static Angle radians(double value) {
        return new Angle(Guards.requireFinite(value, "value") * 180 / Math.PI);
    }

    /** No turn at all. */
    public static Angle zero() {
        return new Angle(0);
    }

    /**
     * The direction of the line from a to b, which is how an edge candidate reports its direction.
     *
     * @throws BadArgumentError when the two points are the same
     */
    public static Angle between(Point a, Point b) {
        if (a.x() == b.x() && a.y() == b.y()) {
            throw new BadArgumentError("two identical points have no direction", Map.of("a", a.toString()));
        }
        return radians(StrictMath.atan2(b.y() - a.y(), b.x() - a.x()));
    }

    /**
     * Circular mean of a set of angles. A plain average of minus one hundred and seventy nine and
     * one hundred and seventy nine gives zero, which is wrong by half a turn, and edge candidates
     * near the wrap really do land like that.
     *
     * @return the mean, {@linkplain #wrapped() wrapped}
     * @throws BadArgumentError when there are no angles, or they cancel out
     */
    public static Angle meanAngle(List<Angle> angles) {
        if (angles.isEmpty()) {
            throw new BadArgumentError("no angles to average", Map.of());
        }
        double sx = 0;
        double sy = 0;
        for (Angle angle : angles) {
            sx += StrictMath.cos(angle.radians());
            sy += StrictMath.sin(angle.radians());
        }
        if (Num.approxEqual(sx, 0, 1e-12) && Num.approxEqual(sy, 0, 1e-12)) {
            throw new BadArgumentError("those angles cancel out and have no mean", Map.of("count", angles.size()));
        }
        return radians(StrictMath.atan2(sy, sx)).wrapped();
    }

    /**
     * The mean direction of a set of undirected lines. Angles that describe a line are the same
     * when they differ by a half turn, so the mean has to be taken on the doubled angle.
     *
     * @throws BadArgumentError when there are no angles, or they cancel out
     */
    public static Angle meanLineAngle(List<Angle> angles) {
        List<Angle> doubled = angles.stream().map(a -> a.times(2)).toList();
        return meanAngle(doubled).times(0.5).wrapped();
    }

    /** The angle in radians. */
    public double radians() {
        return degrees * Math.PI / 180;
    }

    /** True for exactly no turn; a whole turn is not zero until it is {@linkplain #wrapped() wrapped}. */
    public boolean isZero() {
        return degrees == 0;
    }

    /**
     * Folded into the half open turn that runs from just above minus one hundred and eighty up to
     * and including one hundred and eighty. Never minus zero.
     */
    public Angle wrapped() {
        double d = degrees % 360;
        if (d <= -180) {
            d += 360;
        }
        if (d > 180) {
            d -= 360;
        }
        return new Angle(d == 0 ? 0 : d);
    }

    /**
     * Split into the quarter turns a lossless rotation can handle and the small residual a
     * resampler has to deal with. The residual lands in the half open band from minus forty five
     * up to and including forty five.
     */
    public Split split() {
        double wrapped = wrapped().degrees;
        int quarterTurns = (int) Math.round(wrapped / 90);
        double residual = wrapped - quarterTurns * 90;
        if (residual <= -45) {
            residual += 90;
            quarterTurns -= 1;
        }
        quarterTurns = ((quarterTurns % 4) + 4) % 4;
        return new Split(quarterTurns, new Angle(residual == 0 ? 0 : residual));
    }

    /** The two angles added. */
    public Angle plus(Angle other) {
        return new Angle(degrees + other.degrees);
    }

    /** The other angle taken from this one. */
    public Angle minus(Angle other) {
        return new Angle(degrees - other.degrees);
    }

    /**
     * This angle multiplied by the factor.
     *
     * @throws BadArgumentError when the factor is not finite
     */
    public Angle times(double factor) {
        Guards.requireFinite(factor, "factor");
        return new Angle(degrees * factor);
    }

    /** The same turn the other way. */
    public Angle negated() {
        return new Angle(-degrees);
    }

    /** The magnitude of this angle; this same instance when it is not negative. */
    public Angle abs() {
        return degrees < 0 ? new Angle(-degrees) : this;
    }

    /** The shortest way round from this angle to the other, signed and {@linkplain #wrapped() wrapped}. */
    public Angle shortestTo(Angle other) {
        return other.minus(this).wrapped();
    }

    /** True when the other angle is within a billionth of a degree, the short way round. */
    public boolean approxEquals(Angle other) {
        return approxEquals(other, Num.DEFAULT_EPSILON);
    }

    /** True when the other angle is within the tolerance, the short way round; never for null. */
    public boolean approxEquals(Angle other, double toleranceDegrees) {
        return other != null && Num.approxEqual(shortestTo(other).degrees, 0, toleranceDegrees);
    }

    /** True when this angle, wrapped, is no further from zero than the tolerance, such as a skew limit. */
    public boolean isNearZero(double toleranceDegrees) {
        return Math.abs(wrapped().degrees) <= toleranceDegrees;
    }

    /** The degrees, which is how an angle is written in a sidecar. */
    @Override
    public Double toJson() {
        return degrees;
    }

    /** The degrees to four places, such as {@code 1.2346 deg}. */
    @Override
    public String toString() {
        return NumberText.of(Num.roundTo(degrees, 4)) + " deg";
    }

    /**
     * An angle taken apart for rotating a capture: whole quarter turns, which are lossless, and the
     * residual left for a resampler.
     *
     * @param quarterTurns clockwise quarter turns, zero through three
     * @param residual what is left, above minus forty five and up to forty five degrees
     */
    public record Split(int quarterTurns, Angle residual) {
        /** A split of the given parts. */
        public Split {
            Objects.requireNonNull(residual, "residual");
        }
    }
}
