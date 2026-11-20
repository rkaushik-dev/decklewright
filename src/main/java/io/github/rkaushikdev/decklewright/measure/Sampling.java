package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sampling frequency, which the guidelines call ppi and everyone in the lab calls dpi.
 *
 * <p>It is the only bridge between a pixel count and a real size, so every conversion in the tree
 * goes through one of these rather than a loose number that might have come from anywhere. A
 * scanner need not sample both axes alike, so the frequency is kept per {@link Axis}; the
 * conversions read {@link Axis#X} unless told otherwise.
 *
 * <p>{@link #equals(Object)} is exact; {@link #approxEquals(Sampling, double)} compares within a
 * tolerance in ppi.
 *
 * @param x pixels per inch across the bed, above zero
 * @param y pixels per inch down the bed, above zero
 */
public record Sampling(double x, double y) implements JsonValue {

    /** Frequencies this close, in ppi, are the same frequency. */
    private static final double SAME_PPI = 1e-6;

    /**
     * A frequency of the given pixels per inch on each axis.
     *
     * @throws BadArgumentError when either is not a finite number above zero
     */
    public Sampling {
        Guards.requirePositive(x, "x");
        Guards.requirePositive(y, "y");
    }

    /**
     * A frequency with the one figure on both axes.
     *
     * @throws BadArgumentError when the figure is not a finite number above zero
     */
    public static Sampling of(double ppi) {
        return new Sampling(ppi, ppi);
    }

    /**
     * Derives the frequency from a known object size and the pixels it occupied, which is how the
     * copy stand gets calibrated against the scale bar.
     *
     * @return pixels per inch
     * @throws BadArgumentError when the pixel count or the length is not a finite number above zero
     */
    public static double ppiFrom(double pixels, double length, Unit unit) {
        Guards.requirePositive(pixels, "pixels");
        Guards.requirePositive(length, "length");
        return pixels / Units.toInches(length, unit);
    }

    /** True when both axes are sampled alike, to within a millionth of a ppi. */
    public boolean isSquare() {
        return Num.approxEqual(x, y, SAME_PPI);
    }

    /** The pixels per inch on the given axis. */
    public double along(Axis axis) {
        return switch (Objects.requireNonNull(axis, "axis")) {
            case X -> x;
            case Y -> y;
        };
    }

    /** How many pixels a length occupies across the bed. */
    public double toPixels(double value, Unit unit) {
        return toPixels(value, unit, Axis.X);
    }

    /**
     * How many pixels a length occupies on the given axis.
     *
     * @throws BadArgumentError when the value is not finite
     */
    public double toPixels(double value, Unit unit, Axis axis) {
        return Units.toInches(value, unit) * along(axis);
    }

    /** How long a run of pixels across the bed is, in the given unit. */
    public double toLength(double pixels, Unit unit) {
        return toLength(pixels, unit, Axis.X);
    }

    /** How long a run of pixels on the given axis is, in the given unit. */
    public double toLength(double pixels, Unit unit, Axis axis) {
        return Units.fromInches(pixels / along(axis), unit);
    }

    /** Samples per one of the unit across the bed, such as ten per millimetre at 254 ppi. */
    public double per(Unit unit) {
        return per(unit, Axis.X);
    }

    /** Samples per one of the unit on the given axis. */
    public double per(Unit unit, Axis axis) {
        return along(axis) * Units.convert(1, unit, Unit.INCH);
    }

    /**
     * Both axes multiplied by the factor.
     *
     * @throws BadArgumentError when the factor is not a finite number above zero
     */
    public Sampling scaled(double factor) {
        Guards.requirePositive(factor, "factor");
        return new Sampling(x * factor, y * factor);
    }

    /** True when the other is within a millionth of a ppi on both axes; never for null. */
    public boolean approxEquals(Sampling other) {
        return approxEquals(other, SAME_PPI);
    }

    /** True when the other is within {@code tolerance} ppi on both axes; never for null. */
    public boolean approxEquals(Sampling other, double tolerance) {
        return other != null && Num.approxEqual(x, other.x, tolerance) && Num.approxEqual(y, other.y, tolerance);
    }

    /** {@code {x, y}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("x", x);
        json.put("y", y);
        return json;
    }

    /** One figure to two places for square pixels, such as {@code 600 ppi}, and both otherwise, such as {@code 600x300 ppi}. */
    @Override
    public String toString() {
        if (isSquare()) {
            return NumberText.of(Num.roundTo(x, 2)) + " ppi";
        }
        return NumberText.of(Num.roundTo(x, 2)) + "x" + NumberText.of(Num.roundTo(y, 2)) + " ppi";
    }

    /**
     * Reads a frequency from what a caller or a json document may hold: a sampling, which comes back
     * as it is, a bare ppi, or a map with {@code x} and {@code y} entries.
     *
     * @throws BadArgumentError when the value is none of those, or a figure in it is not a finite
     *     number above zero
     */
    public static Sampling toSampling(Object value) {
        if (value instanceof Sampling sampling) {
            return sampling;
        }
        if (value instanceof Number ppi) {
            return of(ppi.doubleValue());
        }
        if (value instanceof Map<?, ?> bag && bag.containsKey("x") && bag.containsKey("y")) {
            return new Sampling(Guards.requirePositive(bag.get("x"), "x"), Guards.requirePositive(bag.get("y"), "y"));
        }
        throw new BadArgumentError(
                "cannot read a sampling frequency from that", Collections.singletonMap("value", value));
    }

    /**
     * How many pixels a length has to occupy to reach a stated frequency. Rounded up, because falling
     * one pixel short of the funder's floor fails the check.
     *
     * @return a whole number of pixels
     * @throws BadArgumentError when the length is not finite, or the frequency is not above zero
     */
    public static double requiredPixels(double length, Unit unit, double ppi) {
        Guards.requirePositive(ppi, "ppi");
        return Math.ceil(Units.toInches(length, unit) * ppi);
    }

    /**
     * The frequency an object of this size would be captured at if it filled the given pixel extent.
     * Used to say what a derivative actually achieved.
     *
     * @throws BadArgumentError as {@link #ppiFrom(double, double, Unit)} does
     */
    public static Sampling achievedSampling(double pixels, double length, Unit unit) {
        return of(ppiFrom(pixels, length, unit));
    }
}
