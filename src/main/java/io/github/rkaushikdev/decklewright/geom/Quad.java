package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Four corners in order, top left then clockwise.
 *
 * <p>A sheet on the copy stand is never an axis aligned rectangle. It is skewed, and if the stand
 * is out of level it is very slightly keystoned as well. The detector reports a quad, the deskew
 * turns the quad into an angle, and only after that does anything in the tree get to talk about
 * rectangles again.
 *
 * <p>A quad is a value: {@link #equals(Object)} compares the corners exactly, and {@link
 * #approxEquals(Quad, double)} corner by corner within a tolerance.
 */
public final class Quad implements JsonValue {
    private final List<Point> corners;

    /**
     * A quad of the given corners.
     *
     * @param corners exactly four, top left then clockwise; each a {@link Point} or anything {@link
     *     Point#from(Object)} reads, such as a pair of numbers
     * @throws BadArgumentError when a corner cannot be read as a point
     * @throws GeometryError when there are not exactly four corners
     */
    public Quad(List<?> corners) {
        this.corners = List.copyOf(fourCorners(corners));
    }

    /** The quad of the rect's corners. */
    public static Quad fromRect(Rect rect) {
        return new Quad(rect.corners());
    }

    /**
     * Puts four corners into the order the rest of the tree expects. The two with the smaller sum of
     * coordinates are the top pair, and within each pair the smaller x is the left one. That holds
     * for any sheet skewed by less than a quarter turn, which is every sheet an operator will ever
     * place.
     *
     * @param corners exactly four, in any order; each a {@link Point} or anything {@link
     *     Point#from(Object)} reads
     * @throws BadArgumentError when a corner cannot be read as a point
     * @throws GeometryError when there are not exactly four corners
     */
    public static Quad ordered(List<?> corners) {
        List<Point> list = fourCorners(corners);
        Point middle = Point.centroid(list);
        List<Point> sorted = new ArrayList<>(list);
        // Compared by the sign of the difference, so bearings of minus zero and zero tie, as they
        // always have.
        sorted.sort((p, q) -> (int) Math.signum(bearing(p, middle) - bearing(q, middle)));
        int start = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i += 1) {
            double score = sorted.get(i).x() + sorted.get(i).y();
            if (score < best) {
                best = score;
                start = i;
            }
        }
        return new Quad(List.of(
                sorted.get(start),
                sorted.get((start + 1) % 4),
                sorted.get((start + 2) % 4),
                sorted.get((start + 3) % 4)));
    }

    /** The four corners, top left then clockwise, in a list that cannot be changed. */
    public List<Point> corners() {
        return corners;
    }

    /** The first corner. */
    public Point topLeft() {
        return corners.get(0);
    }

    /** The second corner. */
    public Point topRight() {
        return corners.get(1);
    }

    /** The third corner. */
    public Point bottomRight() {
        return corners.get(2);
    }

    /** The fourth corner. */
    public Point bottomLeft() {
        return corners.get(3);
    }

    /** The mean of the four corners. */
    public Point centre() {
        return Point.centroid(corners);
    }

    /**
     * Shoelace area. Always positive, because the corner order is fixed and a negative reading
     * would only mean the caller ignored that.
     */
    public double area() {
        double acc = 0;
        for (int i = 0; i < 4; i += 1) {
            Point a = corners.get(i);
            Point b = corners.get((i + 1) % 4);
            acc += a.x() * b.y() - b.x() * a.y();
        }
        return Math.abs(acc) / 2;
    }

    /** The smallest axis aligned rect touching every corner. */
    public Rect boundingRect() {
        return Rect.bounding(corners);
    }

    /** The lengths of the top, right, bottom and left edges, matching the corner order, in a new array. */
    public double[] edgeLengths() {
        return new double[] {
            topLeft().distanceTo(topRight()),
            topRight().distanceTo(bottomRight()),
            bottomRight().distanceTo(bottomLeft()),
            bottomLeft().distanceTo(topLeft())
        };
    }

    /**
     * The direction of each edge, in the same order as {@link #edgeLengths()}, each running from one
     * corner to the next clockwise.
     *
     * @throws BadArgumentError when two neighbouring corners are the same point
     */
    public List<Angle> edgeAngles() {
        return List.of(
                Angle.between(topLeft(), topRight()),
                Angle.between(topRight(), bottomRight()),
                Angle.between(bottomRight(), bottomLeft()),
                Angle.between(bottomLeft(), topLeft()));
    }

    /**
     * How far the sheet is turned, taken from the two long horizontal edges treated as undirected
     * lines so the bottom edge running right to left does not cancel the top one.
     *
     * @throws BadArgumentError when two neighbouring corners are the same point
     */
    public Angle skew() {
        List<Angle> angles = edgeAngles();
        return Angle.meanLineAngle(List.of(angles.get(0), angles.get(2)));
    }

    /** True when every turn goes the same way round; a turn too small to measure counts either way. */
    public boolean isConvex() {
        int sign = 0;
        for (int i = 0; i < 4; i += 1) {
            Point a = corners.get(i);
            Point b = corners.get((i + 1) % 4);
            Point c = corners.get((i + 2) % 4);
            double cross = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
            if (Math.abs(cross) < 1e-12) {
                continue;
            }
            int current = cross > 0 ? 1 : -1;
            if (sign == 0) {
                sign = current;
            } else if (sign != current) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when opposite edges match within a pixel and the corners are square within half a
     * degree.
     *
     * @throws BadArgumentError when two neighbouring corners are the same point
     * @see #isRectangular(double, double)
     */
    public boolean isRectangular() {
        return isRectangular(1, 0.5);
    }

    /**
     * True when opposite edges match in length and the corners are square, both within the
     * tolerances given. A keystoned capture fails this and the operator gets told to level the
     * stand.
     *
     * @param lengthPixels how far apart opposite edge lengths may be
     * @param cornerDegrees how far from a right angle each corner may be
     * @throws BadArgumentError when two neighbouring corners are the same point
     */
    public boolean isRectangular(double lengthPixels, double cornerDegrees) {
        double[] lengths = edgeLengths();
        if (Math.abs(lengths[0] - lengths[2]) > lengthPixels) {
            return false;
        }
        if (Math.abs(lengths[1] - lengths[3]) > lengthPixels) {
            return false;
        }
        List<Angle> angles = edgeAngles();
        for (int i = 0; i < 4; i += 1) {
            double turn =
                    angles.get(i).shortestTo(angles.get((i + 1) % 4)).abs().degrees();
            if (Math.abs(turn - 90) > cornerDegrees) {
                return false;
            }
        }
        return true;
    }

    /** The quad with every corner carried through the matrix. */
    public Quad transformedBy(Matrix matrix) {
        return new Quad(corners.stream().map(matrix::apply).toList());
    }

    /** True when every corner is within a billionth of a pixel of the other's. */
    public boolean approxEquals(Quad other) {
        return approxEquals(other, Num.DEFAULT_EPSILON);
    }

    /** True when every corner is within {@code tolerance} pixels of the other's; never for null. */
    public boolean approxEquals(Quad other, double tolerance) {
        if (other == null) {
            return false;
        }
        for (int i = 0; i < 4; i += 1) {
            if (!corners.get(i).approxEquals(other.corners.get(i), tolerance)) {
                return false;
            }
        }
        return true;
    }

    /** True when the area is within a billionth of a square pixel of zero. */
    public boolean isDegenerate() {
        return isDegenerate(Num.DEFAULT_EPSILON);
    }

    /** True when the area is within the tolerance of zero. */
    public boolean isDegenerate(double tolerance) {
        return Num.approxEqual(area(), 0, tolerance);
    }

    /** The four corners as {@code {x, y}} maps, in a new list the caller may keep. */
    @Override
    public List<Map<String, Object>> toJson() {
        return corners.stream().map(Point::toJson).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Quad quad && corners.equals(quad.corners);
    }

    @Override
    public int hashCode() {
        return corners.hashCode();
    }

    /** The corners in order, separated by spaces, such as {@code (0, 0) (1, 0) (1, 1) (0, 1)}. */
    @Override
    public String toString() {
        return corners.stream().map(Point::toString).collect(Collectors.joining(" "));
    }

    private static List<Point> fourCorners(List<?> corners) {
        List<Point> list = Guards.requireListOf(corners, (corner, index) -> Point.from(corner), "corners");
        if (list.size() != 4) {
            throw new GeometryError("a quad needs exactly four corners", Map.of("count", list.size()));
        }
        return list;
    }

    private static double bearing(Point corner, Point middle) {
        return StrictMath.atan2(corner.y() - middle.y(), corner.x() - middle.x());
    }
}
