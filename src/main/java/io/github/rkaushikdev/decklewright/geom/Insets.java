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
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Four independent edge offsets in pixels.
 *
 * <p>Bleed, safe margin and collar retention are all quoted per edge, because a quadrangle sheet
 * has a wide legend collar at the bottom and a narrow one at the top, and treating them the same
 * throws away the legend. An edge may be negative, since an outset is a negative inset. {@link
 * #equals(Object)} is exact; {@link #approxEquals(Insets, double)} compares within a tolerance.
 *
 * @param top pixels in from the top edge
 * @param right pixels in from the right edge
 * @param bottom pixels in from the bottom edge
 * @param left pixels in from the left edge
 */
public record Insets(double top, double right, double bottom, double left) implements JsonValue {

    /**
     * Insets of the given edges, in css order.
     *
     * @throws BadArgumentError when an edge is not finite
     */
    public Insets {
        Guards.requireFinite(top, "top");
        Guards.requireFinite(right, "right");
        Guards.requireFinite(bottom, "bottom");
        Guards.requireFinite(left, "left");
    }

    /** The same amount on every edge. */
    public static Insets uniform(double amount) {
        return new Insets(amount, amount, amount, amount);
    }

    /** One amount for the top and bottom and another for the left and right. */
    public static Insets symmetric(double vertical, double horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    /** Nothing on any edge. */
    public static Insets zero() {
        return new Insets(0, 0, 0, 0);
    }

    /**
     * Reads the shapes a profile writes: insets, which come back as they are, a single number for
     * every edge, a css style list of one, two or four numbers, or a map naming any of {@code top},
     * {@code right}, {@code bottom} and {@code left}, where a missing or null edge is zero.
     *
     * @throws BadArgumentError when the value is none of those, a list has three entries or more
     *     than four, or an edge is not a finite number
     */
    public static Insets from(Object value) {
        if (value instanceof Insets insets) {
            return insets;
        }
        if (value instanceof Number amount) {
            return uniform(amount.doubleValue());
        }
        if (value instanceof List<?> list) {
            return switch (list.size()) {
                case 1 -> edges(list.get(0), list.get(0), list.get(0), list.get(0));
                case 2 -> edges(list.get(0), list.get(1), list.get(0), list.get(1));
                case 4 -> edges(list.get(0), list.get(1), list.get(2), list.get(3));
                default ->
                    throw new BadArgumentError(
                            "an inset list must hold one, two or four numbers", Map.of("length", list.size()));
            };
        }
        if (value instanceof Map<?, ?> bag) {
            return edges(edge(bag, "top"), edge(bag, "right"), edge(bag, "bottom"), edge(bag, "left"));
        }
        throw new BadArgumentError("cannot read insets from that", Collections.singletonMap("value", value));
    }

    /** The left and right edges together. */
    public double horizontal() {
        return left + right;
    }

    /** The top and bottom edges together. */
    public double vertical() {
        return top + bottom;
    }

    /** True when every edge is exactly zero. */
    public boolean isZero() {
        return top == 0 && right == 0 && bottom == 0 && left == 0;
    }

    /** True when every edge is the same. */
    public boolean isUniform() {
        return top == right && right == bottom && bottom == left;
    }

    /** The widest edge. */
    public double largest() {
        return Math.max(Math.max(Math.max(top, right), bottom), left);
    }

    /**
     * Pulls the edges of a rect in by these amounts. A rect that would invert, or be used up
     * exactly, collapses to zero size at its centre instead.
     */
    public Rect applyTo(Rect rect) {
        double width = rect.width() - horizontal();
        double height = rect.height() - vertical();
        if (width <= 0 || height <= 0) {
            Point centre = rect.centre();
            return new Rect(centre.x(), centre.y(), 0, 0);
        }
        return new Rect(rect.x() + left, rect.y() + top, width, height);
    }

    /** Pushes the edges of a rect out by these amounts. */
    public Rect expandFrom(Rect rect) {
        return negated().applyTo(rect);
    }

    /** Every edge the other way, which turns an inset into an outset. */
    public Insets negated() {
        return new Insets(-top, -right, -bottom, -left);
    }

    /** The two added edge by edge. */
    public Insets plus(Insets other) {
        return new Insets(top + other.top, right + other.right, bottom + other.bottom, left + other.left);
    }

    /**
     * Every edge multiplied by the factor.
     *
     * @throws BadArgumentError when the factor is not finite
     */
    public Insets times(double factor) {
        Guards.requireFinite(factor, "factor");
        return new Insets(top * factor, right * factor, bottom * factor, left * factor);
    }

    /**
     * Edge by edge maximum, which is how two rules that both want a margin are combined. The wider
     * demand wins on each edge independently.
     */
    public Insets maxWith(Insets other) {
        return new Insets(
                Math.max(top, other.top),
                Math.max(right, other.right),
                Math.max(bottom, other.bottom),
                Math.max(left, other.left));
    }

    /** Every edge rounded to the nearest whole pixel, halves towards positive infinity. */
    public Insets rounded() {
        return rounded(Num::roundHalfUp);
    }

    /** Every edge put through the rounder, such as {@code Math::floor}. */
    public Insets rounded(DoubleUnaryOperator rounder) {
        return new Insets(
                rounder.applyAsDouble(top),
                rounder.applyAsDouble(right),
                rounder.applyAsDouble(bottom),
                rounder.applyAsDouble(left));
    }

    /** True when every edge of the other is within a billionth of a pixel of this one's. */
    public boolean approxEquals(Insets other) {
        return approxEquals(other, Num.DEFAULT_EPSILON);
    }

    /** True when every edge of the other is within {@code tolerance} pixels of this one's; never for null. */
    public boolean approxEquals(Insets other, double tolerance) {
        return other != null
                && Num.approxEqual(top, other.top, tolerance)
                && Num.approxEqual(right, other.right, tolerance)
                && Num.approxEqual(bottom, other.bottom, tolerance)
                && Num.approxEqual(left, other.left, tolerance);
    }

    /** {@code {top, right, bottom, left}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("top", top);
        json.put("right", right);
        json.put("bottom", bottom);
        json.put("left", left);
        return json;
    }

    /**
     * The edges to three places: {@code 3 all round} when they match, otherwise all four in css
     * order, such as {@code 1 2 3 4}.
     */
    @Override
    public String toString() {
        if (isUniform()) {
            return NumberText.of(Num.roundTo(top, 3)) + " all round";
        }
        return DoubleStream.of(top, right, bottom, left)
                .mapToObj(edge -> NumberText.of(Num.roundTo(edge, 3)))
                .collect(Collectors.joining(" "));
    }

    /** Insets from untyped edges, each checked in css order under its own name. */
    private static Insets edges(Object top, Object right, Object bottom, Object left) {
        return new Insets(
                Guards.requireFinite(top, "top"),
                Guards.requireFinite(right, "right"),
                Guards.requireFinite(bottom, "bottom"),
                Guards.requireFinite(left, "left"));
    }

    private static Object edge(Map<?, ?> bag, String name) {
        return Objects.requireNonNullElse(bag.get(name), 0.0);
    }
}
