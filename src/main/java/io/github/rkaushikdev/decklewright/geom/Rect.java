package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * An axis aligned rectangle in image space.
 *
 * <p>The right and bottom edges are exclusive, so a rect at the origin with a width of ten covers
 * columns zero through nine. Every crop in the tree is one of these, and half the bugs the lab has
 * ever filed came from someone disagreeing about that last column.
 *
 * <p>A planned crop is real valued; only once it has been rounded, which {@link
 * Rounding#isWholePixel(Rect)} checks, can it be handed to the raster layer as whole pixels.
 * {@link #equals(Object)} is exact; {@link #approxEquals(Rect, double)} compares within a
 * tolerance.
 *
 * @param x the left edge
 * @param y the top edge
 * @param width pixels across, never negative
 * @param height pixels down, never negative
 */
public record Rect(double x, double y, double width, double height) implements JsonValue {

    /**
     * A rect with its top left corner at {@code (x, y)}.
     *
     * @throws BadArgumentError when a value is not finite, or an extent is negative
     */
    public Rect {
        Guards.requireFinite(x, "x");
        Guards.requireFinite(y, "y");
        Guards.requireNonNegative(width, "width");
        Guards.requireNonNegative(height, "height");
    }

    /**
     * A rect with its top left corner at {@code (x, y)}.
     *
     * @throws BadArgumentError when a value is not finite, or an extent is negative
     */
    public static Rect of(double x, double y, double width, double height) {
        return new Rect(x, y, width, height);
    }

    /** A rect with no extent at the origin. */
    public static Rect empty() {
        return new Rect(0, 0, 0, 0);
    }

    /**
     * The rect between the four edges.
     *
     * @throws GeometryError when the right edge is left of the left one, or the bottom above the
     *     top
     */
    public static Rect fromEdges(double left, double top, double right, double bottom) {
        if (right < left || bottom < top) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("left", left);
            details.put("top", top);
            details.put("right", right);
            details.put("bottom", bottom);
            throw new GeometryError("edges are inside out", details);
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    /**
     * The rect with two opposite corners at the points. Unlike {@link #fromEdges(double, double,
     * double, double)} this accepts the corners in any order, because a drag on screen can start at
     * any corner.
     */
    public static Rect fromCorners(Point a, Point b) {
        double left = Math.min(a.x(), b.x());
        double top = Math.min(a.y(), b.y());
        return new Rect(left, top, Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    /** The rect of the given size centred on the point. */
    public static Rect fromCentre(Point centre, Size size) {
        return new Rect(centre.x() - size.width() / 2, centre.y() - size.height() / 2, size.width(), size.height());
    }

    /** The rect of the given size with its top left corner at the point. */
    public static Rect fromOrigin(Point origin, Size size) {
        return new Rect(origin.x(), origin.y(), size.width(), size.height());
    }

    /**
     * The smallest rect whose edges touch every point. The points lie on its edges, so a point on
     * the right or bottom one is not {@linkplain #contains(Point) contained}.
     *
     * @throws BadArgumentError when there are no points
     */
    public static Rect bounding(List<Point> points) {
        if (points.isEmpty()) {
            throw new BadArgumentError("no points to bound", Map.of());
        }
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            if (p.x() < left) {
                left = p.x();
            }
            if (p.y() < top) {
                top = p.y();
            }
            if (p.x() > right) {
                right = p.x();
            }
            if (p.y() > bottom) {
                bottom = p.y();
            }
        }
        return fromEdges(left, top, right, bottom);
    }

    /** The left edge, which is {@link #x()}. */
    public double left() {
        return x;
    }

    /** The top edge, which is {@link #y()}. */
    public double top() {
        return y;
    }

    /** The right edge, exclusive. */
    public double right() {
        return x + width;
    }

    /** The bottom edge, exclusive. */
    public double bottom() {
        return y + height;
    }

    /** The top left corner. */
    public Point origin() {
        return new Point(x, y);
    }

    /** The extent without the position. */
    public Size size() {
        return new Size(width, height);
    }

    /** The middle of the rect, which need not be a whole pixel. */
    public Point centre() {
        return new Point(x + width / 2, y + height / 2);
    }

    /** Width times height, in square pixels. */
    public double area() {
        return width * height;
    }

    /** True when either extent is zero. */
    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /**
     * The four corners, clockwise from the top left, which is the order the corner refinement and
     * the quad both expect.
     */
    public List<Point> corners() {
        return List.of(
                new Point(left(), top()),
                new Point(right(), top()),
                new Point(right(), bottom()),
                new Point(left(), bottom()));
    }

    /** The same rect moved by the given amounts. */
    public Rect translated(double dx, double dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    /** Position and extent both multiplied by the factor, which is what a change of resolution does. */
    public Rect scaled(double factor) {
        return scaled(factor, factor);
    }

    /** Position and extent multiplied by a factor per axis. */
    public Rect scaled(double factor, double factorY) {
        return new Rect(x * factor, y * factorY, width * factor, height * factorY);
    }

    /** The same top left corner with another extent. */
    public Rect withSize(Size size) {
        return new Rect(x, y, size.width(), size.height());
    }

    /** The same extent centred on another point. */
    public Rect withCentre(Point centre) {
        return fromCentre(centre, size());
    }

    /** The same extent with its top left corner at another point. */
    public Rect withOrigin(Point origin) {
        return new Rect(origin.x(), origin.y(), width, height);
    }

    /**
     * Every edge pulled in by the amount, about the centre. A shrink that would invert the rect
     * collapses to zero on that axis rather than going negative.
     */
    public Rect inset(double amount) {
        return inset(amount, amount);
    }

    /**
     * The left and right edges pulled in by {@code amount} and the top and bottom by {@code
     * amountY}, about the centre. A shrink that would invert the rect collapses to zero on that
     * axis rather than going negative.
     */
    public Rect inset(double amount, double amountY) {
        double insetWidth = Math.max(0, width - amount * 2);
        double insetHeight = Math.max(0, height - amountY * 2);
        Point centre = centre();
        return new Rect(centre.x() - insetWidth / 2, centre.y() - insetHeight / 2, insetWidth, insetHeight);
    }

    /** Every edge pushed out by the amount, about the centre. */
    public Rect outset(double amount) {
        return outset(amount, amount);
    }

    /** The left and right edges pushed out by {@code amount} and the top and bottom by {@code amountY}. */
    public Rect outset(double amount, double amountY) {
        return inset(-amount, -amountY);
    }

    /** True when the point lies inside, counting the left and top edges but not the right and bottom. */
    public boolean contains(Point point) {
        return point.x() >= left() && point.x() < right() && point.y() >= top() && point.y() < bottom();
    }

    /** True when the other rect lies wholly inside this one; sharing an edge still counts. */
    public boolean containsRect(Rect other) {
        return other.left() >= left() && other.top() >= top() && other.right() <= right() && other.bottom() <= bottom();
    }

    /** True when the two overlap by some area; touching along an edge is no overlap. */
    public boolean intersects(Rect other) {
        return !intersect(other).isEmpty();
    }

    /** The overlap of the two, or {@link #empty()} when they have none. */
    public Rect intersect(Rect other) {
        double left = Math.max(left(), other.left());
        double top = Math.max(top(), other.top());
        double right = Math.min(right(), other.right());
        double bottom = Math.min(bottom(), other.bottom());
        if (right <= left || bottom <= top) {
            return empty();
        }
        return fromEdges(left, top, right, bottom);
    }

    /**
     * The smallest rect holding both. An empty rect is ignored wherever it sits, so the other one
     * comes back as the same instance.
     */
    public Rect union(Rect other) {
        if (isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        return fromEdges(
                Math.min(left(), other.left()),
                Math.min(top(), other.top()),
                Math.max(right(), other.right()),
                Math.max(bottom(), other.bottom()));
    }

    /**
     * This rect slid until it sits inside the bound, keeping its size. If it is as large as the
     * bound or larger on an axis it is centred on that axis instead, which is what a planner wants
     * when the crop is deliberately oversized.
     */
    public Rect nudgedInto(Rect bound) {
        double nudgedX;
        double nudgedY;
        if (width >= bound.width) {
            nudgedX = bound.left() + (bound.width - width) / 2;
        } else {
            nudgedX = Math.min(Math.max(x, bound.left()), bound.right() - width);
        }
        if (height >= bound.height) {
            nudgedY = bound.top() + (bound.height - height) / 2;
        } else {
            nudgedY = Math.min(Math.max(y, bound.top()), bound.bottom() - height);
        }
        return new Rect(nudgedX, nudgedY, width, height);
    }

    /** Every edge rounded to the nearest whole pixel, halves towards positive infinity; never inverted. */
    public Rect rounded() {
        return rounded(Num::roundHalfUp);
    }

    /**
     * Every edge put through the rounder, such as {@code Math::floor}. An extent the rounding would
     * invert stays at zero instead.
     */
    public Rect rounded(DoubleUnaryOperator rounder) {
        double left = rounder.applyAsDouble(left());
        double top = rounder.applyAsDouble(top());
        double right = rounder.applyAsDouble(right());
        double bottom = rounder.applyAsDouble(bottom());
        return fromEdges(left, top, Math.max(left, right), Math.max(top, bottom));
    }

    /**
     * Grown to whole pixels in every direction, which is what a crop has to do before it can be
     * handed to the raster layer.
     */
    public Rect outward() {
        return fromEdges(Math.floor(left()), Math.floor(top()), Math.ceil(right()), Math.ceil(bottom()));
    }

    /** True when the other rect is within a billionth of a pixel on position and extent. */
    public boolean approxEquals(Rect other) {
        return approxEquals(other, Num.DEFAULT_EPSILON);
    }

    /** True when the other rect is within {@code tolerance} pixels on position and extent; never for null. */
    public boolean approxEquals(Rect other, double tolerance) {
        return other != null
                && Num.approxEqual(x, other.x, tolerance)
                && Num.approxEqual(y, other.y, tolerance)
                && Num.approxEqual(width, other.width, tolerance)
                && Num.approxEqual(height, other.height, tolerance);
    }

    /** {@code {x, y, width, height}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("x", x);
        json.put("y", y);
        json.put("width", width);
        json.put("height", height);
        return json;
    }

    /** The geometry form the log uses, to three places: width x height + x + y, such as {@code 30x40+10+20}. */
    @Override
    public String toString() {
        return NumberText.of(Num.roundTo(width, 3))
                + "x"
                + NumberText.of(Num.roundTo(height, 3))
                + "+"
                + NumberText.of(Num.roundTo(x, 3))
                + "+"
                + NumberText.of(Num.roundTo(y, 3));
    }
}
