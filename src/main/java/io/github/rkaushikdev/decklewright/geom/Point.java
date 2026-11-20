package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
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
 * A position in image space, x to the right and y down, measured in pixels of the capture it came
 * from.
 *
 * <p>Nothing here is in physical units on purpose. A point only becomes a real place once you know
 * the sampling frequency, and that conversion belongs to the caller who knows which capture this
 * is.
 *
 * <p>Coordinates are real numbers: a refined corner or a centroid lands between pixels. {@link
 * #equals(Object)} is exact, as for any record; {@link #approxEquals(Point, double)} is the
 * comparison within a pixel tolerance that measurements want.
 *
 * @param x pixels to the right of the capture's left edge
 * @param y pixels below the capture's top edge
 */
public record Point(double x, double y) implements JsonValue {

    /**
     * A point at the given coordinates.
     *
     * @throws BadArgumentError when either coordinate is not finite
     */
    public Point {
        Guards.requireFinite(x, "x");
        Guards.requireFinite(y, "y");
    }

    /**
     * A point at the given coordinates.
     *
     * @throws BadArgumentError when either coordinate is not finite
     */
    public static Point of(double x, double y) {
        return new Point(x, y);
    }

    /** The top left corner of the capture. */
    public static Point origin() {
        return new Point(0, 0);
    }

    /**
     * Reads a point from what a caller or a json document may hold: a point, which comes back as
     * it is, a list of two numbers, or a map with {@code x} and {@code y} entries.
     *
     * @throws BadArgumentError when the value is none of those, or a coordinate is not a finite
     *     number
     */
    public static Point from(Object value) {
        if (value instanceof Point point) {
            return point;
        }
        if (value instanceof List<?> pair && pair.size() == 2) {
            return new Point(Guards.requireFinite(pair.get(0), "x"), Guards.requireFinite(pair.get(1), "y"));
        }
        if (value instanceof Map<?, ?> bag && bag.containsKey("x") && bag.containsKey("y")) {
            return new Point(Guards.requireFinite(bag.get("x"), "x"), Guards.requireFinite(bag.get("y"), "y"));
        }
        throw new BadArgumentError("cannot read a point from that", Collections.singletonMap("value", value));
    }

    /**
     * The mean of the points, which for the four corners of a quad is its centre.
     *
     * @throws BadArgumentError when there are no points
     */
    public static Point centroid(List<Point> points) {
        if (points.isEmpty()) {
            throw new BadArgumentError("no points to average", Map.of());
        }
        double sx = 0;
        double sy = 0;
        for (Point p : points) {
            sx += p.x;
            sy += p.y;
        }
        return new Point(sx / points.size(), sy / points.size());
    }

    /** This point moved by the other one, read as an offset. */
    public Point plus(Point other) {
        return new Point(x + other.x, y + other.y);
    }

    /** The offset that takes the other point to this one. */
    public Point minus(Point other) {
        return new Point(x - other.x, y - other.y);
    }

    /** Both coordinates multiplied by the factor. */
    public Point times(double factor) {
        return times(factor, factor);
    }

    /** The coordinates multiplied by a factor each, for when the axes scale differently. */
    public Point times(double factor, double factorY) {
        return new Point(x * factor, y * factorY);
    }

    /** This point moved by the given amounts. */
    public Point offset(double dx, double dy) {
        return new Point(x + dx, y + dy);
    }

    /** The straight line distance to the other point, in pixels. */
    public double distanceTo(Point other) {
        return Num.hypot(x - other.x, y - other.y);
    }

    /**
     * Squared distance, for when a comparison is all that is wanted and the square root would just
     * be thrown away.
     */
    public double distanceSquaredTo(Point other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return dx * dx + dy * dy;
    }

    /**
     * This point turned about the centre. Positive angles turn clockwise on screen, which is what
     * an operator sees when they nudge a sheet on the copy stand.
     */
    public Point rotatedAround(Point centre, double radians) {
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        double dx = x - centre.x;
        double dy = y - centre.y;
        return new Point(centre.x + dx * cos - dy * sin, centre.y + dx * sin + dy * cos);
    }

    /** The point the fraction {@code t} of the way to the other; extrapolates outside zero to one. */
    public Point lerpTo(Point other, double t) {
        return new Point(x + (other.x - x) * t, y + (other.y - y) * t);
    }

    /** Both coordinates rounded to the nearest whole pixel, halves towards positive infinity. */
    public Point rounded() {
        return rounded(Num::roundHalfUp);
    }

    /** Both coordinates put through the rounder, such as {@code Math::floor}. */
    public Point rounded(DoubleUnaryOperator rounder) {
        return new Point(rounder.applyAsDouble(x), rounder.applyAsDouble(y));
    }

    /** True when the other point is within a billionth of a pixel on both axes. */
    public boolean approxEquals(Point other) {
        return approxEquals(other, Num.DEFAULT_EPSILON);
    }

    /** True when the other point is within {@code tolerance} pixels on both axes; never for null. */
    public boolean approxEquals(Point other, double tolerance) {
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

    /** The coordinates to three places, such as {@code (1.235, -2)}. */
    @Override
    public String toString() {
        return "(" + NumberText.of(Num.roundTo(x, 3)) + ", " + NumberText.of(Num.roundTo(y, 3)) + ")";
    }
}
