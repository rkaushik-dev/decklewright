package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * An extent in pixels.
 *
 * <p>Zero is allowed because an empty crop is a real answer a rule can produce, but negative is
 * not, and the difference matters when a planner subtracts one inset too many. Extents are real
 * numbers until a crop is rounded for the raster layer. {@link #equals(Object)} is exact; {@link
 * #approxEquals(Size, double)} compares within a tolerance.
 *
 * @param width pixels across, never negative
 * @param height pixels down, never negative
 */
public record Size(double width, double height) implements JsonValue {

    /**
     * A size of the given extents.
     *
     * @throws BadArgumentError when an extent is negative or not finite
     */
    public Size {
        Guards.requireNonNegative(width, "width");
        Guards.requireNonNegative(height, "height");
    }

    /**
     * A size of the given extents.
     *
     * @throws BadArgumentError when an extent is negative or not finite
     */
    public static Size of(double width, double height) {
        return new Size(width, height);
    }

    /** A size with both extents the same. */
    public static Size square(double side) {
        return new Size(side, side);
    }

    /** A size with no extent at all. */
    public static Size empty() {
        return new Size(0, 0);
    }

    /**
     * Reads a size from what a caller or a json document may hold: a size, which comes back as it
     * is, a list of two numbers, or a map with {@code width} and {@code height} entries.
     *
     * @throws BadArgumentError when the value is none of those, or an extent is not a finite number
     *     at or above zero
     */
    public static Size from(Object value) {
        if (value instanceof Size size) {
            return size;
        }
        if (value instanceof List<?> pair && pair.size() == 2) {
            return new Size(
                    Guards.requireNonNegative(pair.get(0), "width"), Guards.requireNonNegative(pair.get(1), "height"));
        }
        if (value instanceof Map<?, ?> bag && bag.containsKey("width") && bag.containsKey("height")) {
            return new Size(
                    Guards.requireNonNegative(bag.get("width"), "width"),
                    Guards.requireNonNegative(bag.get("height"), "height"));
        }
        throw new BadArgumentError("cannot read a size from that", Collections.singletonMap("value", value));
    }

    /**
     * The size, when it has both extents above zero.
     *
     * @param name what the size is, for the message, such as {@code crop}
     * @throws GeometryError when either extent is zero
     */
    public static Size requireNotEmpty(Size size, String name) {
        if (size.isEmpty()) {
            throw new GeometryError(name + " must have both extents above zero", Map.of("size", size.toJson()));
        }
        return size;
    }

    /**
     * The size of the given width with the given aspect.
     *
     * @param aspect width over height
     * @throws BadArgumentError when the aspect or the width is not above zero
     */
    public static Size sizeFromAspect(double aspect, double width) {
        Guards.requirePositive(aspect, "aspect");
        Guards.requirePositive(width, "width");
        return new Size(width, width / aspect);
    }

    /** Width times height, in square pixels. */
    public double area() {
        return width * height;
    }

    /** True when either extent is zero. */
    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /** True when the size is wider than it is tall. */
    public boolean isLandscape() {
        return width > height;
    }

    /** True when the size is taller than it is wide. */
    public boolean isPortrait() {
        return height > width;
    }

    /**
     * Width over height. An empty size has no aspect and asking for one is a mistake worth
     * surfacing rather than a quiet infinity.
     *
     * @throws GeometryError when the height is zero
     */
    public double aspect() {
        if (height == 0) {
            throw new GeometryError("a size with no height has no aspect", Map.of("size", toJson()));
        }
        return width / height;
    }

    /** The size turned a quarter: width and height exchanged. */
    public Size swapped() {
        return new Size(height, width);
    }

    /**
     * Both extents multiplied by the factor.
     *
     * @throws BadArgumentError when the factor is negative or not finite
     */
    public Size scaled(double factor) {
        return scaled(factor, factor);
    }

    /**
     * The extents multiplied by a factor each.
     *
     * @throws BadArgumentError when either factor is negative or not finite
     */
    public Size scaled(double factor, double factorY) {
        Guards.requireNonNegative(factor, "factor");
        Guards.requireNonNegative(factorY, "factorY");
        return new Size(width * factor, height * factorY);
    }

    /** Both extents grown by the amount; a shrink stops at zero rather than going negative. */
    public Size grown(double dw) {
        return grown(dw, dw);
    }

    /** The extents grown by an amount each; a shrink stops at zero rather than going negative. */
    public Size grown(double dw, double dh) {
        return new Size(Math.max(0, width + dw), Math.max(0, height + dh));
    }

    /** Both extents rounded to the nearest whole pixel, halves up. */
    public Size rounded() {
        return rounded(Num::roundHalfUp);
    }

    /** Both extents put through the rounder, such as {@code Math::floor}, and held at zero or above. */
    public Size rounded(DoubleUnaryOperator rounder) {
        return new Size(Math.max(0, rounder.applyAsDouble(width)), Math.max(0, rounder.applyAsDouble(height)));
    }

    /** The largest size with this aspect that fits inside the bound; empty when either side is. */
    public Size containedIn(Size bound) {
        if (isEmpty() || bound.isEmpty()) {
            return empty();
        }
        double factor = Math.min(bound.width / width, bound.height / height);
        return scaled(factor);
    }

    /** The smallest size with this aspect that covers the bound; empty when either side is. */
    public Size covering(Size bound) {
        if (isEmpty() || bound.isEmpty()) {
            return empty();
        }
        double factor = Math.max(bound.width / width, bound.height / height);
        return scaled(factor);
    }

    /** True when neither extent is larger than the other size's. */
    public boolean fitsInside(Size other) {
        return width <= other.width && height <= other.height;
    }

    /**
     * The aspect written the way a work order writes it, reduced to whole numbers when the size is
     * whole, such as {@code 3:2}. Falls back to the extents to two places when it is not.
     */
    public String ratioLabel() {
        if (!isWhole(width) || !isWhole(height) || isEmpty()) {
            return NumberText.of(Num.roundTo(width, 2)) + ":" + NumberText.of(Num.roundTo(height, 2));
        }
        double gcd = Num.gcd(width, height);
        double divisor = gcd == 0 ? 1 : gcd;
        return NumberText.of(width / divisor) + ":" + NumberText.of(height / divisor);
    }

    /** True when the other size is within a billionth of a pixel on both extents. */
    public boolean approxEquals(Size other) {
        return approxEquals(other, Num.DEFAULT_EPSILON);
    }

    /** True when the other size is within {@code tolerance} pixels on both extents; never for null. */
    public boolean approxEquals(Size other, double tolerance) {
        return other != null
                && Num.approxEqual(width, other.width, tolerance)
                && Num.approxEqual(height, other.height, tolerance);
    }

    /** {@code {width, height}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("width", width);
        json.put("height", height);
        return json;
    }

    /** The extents to three places with an x between, such as {@code 1.235x3}. */
    @Override
    public String toString() {
        return NumberText.of(Num.roundTo(width, 3)) + "x" + NumberText.of(Num.roundTo(height, 3));
    }

    private static boolean isWhole(double value) {
        return value == Math.rint(value);
    }
}
