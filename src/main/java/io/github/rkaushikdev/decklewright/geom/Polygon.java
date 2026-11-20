package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A closed polygon with at least three vertices, used for the object outline the mask produces
 * before it is reduced to a quad.
 *
 * <p>Nothing here assumes convexity, because a rolled drawing that has not fully relaxed produces
 * an outline with a genuine dent in one edge. A polygon is a value: {@link #equals(Object)}
 * compares the vertices exactly and in order.
 */
public final class Polygon implements JsonValue {
    private final List<Point> vertices;

    /**
     * A polygon through the given vertices, closing back to the first.
     *
     * @param vertices at least three; each a {@link Point} or anything {@link Point#from(Object)}
     *     reads, such as a pair of numbers
     * @throws BadArgumentError when a vertex cannot be read as a point
     * @throws GeometryError when there are fewer than three vertices
     */
    public Polygon(List<?> vertices) {
        List<Point> list = Guards.requireListOf(vertices, (vertex, index) -> Point.from(vertex), "vertices");
        if (list.size() < 3) {
            throw new GeometryError("a polygon needs at least three vertices", Map.of("count", list.size()));
        }
        this.vertices = List.copyOf(list);
    }

    /** The polygon of the rect's corners, clockwise from the top left. */
    public static Polygon fromRect(Rect rect) {
        return new Polygon(rect.corners());
    }

    /**
     * The convex hull of the points, by Andrew's monotone chain. Returns the hull in the same
     * clockwise on screen order the rest of the tree uses.
     *
     * @param points at least three; each a {@link Point} or anything {@link Point#from(Object)}
     *     reads
     * @throws BadArgumentError when a point cannot be read as one
     * @throws GeometryError when there are fewer than three points, or they all lie on one line
     */
    public static Polygon convexHull(List<?> points) {
        List<Point> list = Guards.requireListOf(points, (point, index) -> Point.from(point), "points");
        if (list.size() < 3) {
            throw new GeometryError("a hull needs at least three points", Map.of("count", list.size()));
        }
        List<Point> sorted = new ArrayList<>(list);
        // Compared by the sign of each difference, so minus zero and zero tie where Double.compare
        // would order them.
        sorted.sort((a, b) -> {
            int byX = (int) Math.signum(a.x() - b.x());
            return byX != 0 ? byX : (int) Math.signum(a.y() - b.y());
        });

        List<Point> lower = new ArrayList<>();
        for (Point p : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0) {
                lower.removeLast();
            }
            lower.add(p);
        }
        List<Point> upper = new ArrayList<>();
        for (Point p : sorted.reversed()) {
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0) {
                upper.removeLast();
            }
            upper.add(p);
        }
        lower.removeLast();
        upper.removeLast();
        List<Point> hull = new ArrayList<>(lower);
        hull.addAll(upper);
        if (hull.size() < 3) {
            throw new GeometryError("those points are collinear and have no hull", Map.of("count", list.size()));
        }
        return new Polygon(hull);
    }

    /** The vertices in order, in a list that cannot be changed. */
    public List<Point> vertices() {
        return vertices;
    }

    /** How many vertices there are. */
    public int count() {
        return vertices.size();
    }

    /**
     * Signed area. Positive means the vertices run clockwise on screen, where y grows downward.
     */
    public double signedArea() {
        double acc = 0;
        for (int i = 0; i < vertices.size(); i += 1) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            acc += a.x() * b.y() - b.x() * a.y();
        }
        return acc / 2;
    }

    /** The enclosed area, whichever way the vertices run. */
    public double area() {
        return Math.abs(signedArea());
    }

    /** True when the vertices run clockwise on screen. */
    public boolean isClockwise() {
        return signedArea() > 0;
    }

    /** The length of the outline, closing edge included. */
    public double perimeter() {
        double acc = 0;
        for (int i = 0; i < vertices.size(); i += 1) {
            acc += vertices.get(i).distanceTo(vertices.get((i + 1) % vertices.size()));
        }
        return acc;
    }

    /** The smallest axis aligned rect touching every vertex. */
    public Rect boundingRect() {
        return Rect.bounding(vertices);
    }

    /**
     * Area weighted centroid, which is not the same as the mean of the vertices once the outline
     * has more detail on one side than the other.
     *
     * @throws GeometryError when the polygon encloses no area
     */
    public Point centroid() {
        double twiceArea = signedArea() * 2;
        if (Math.abs(twiceArea) < 1e-12) {
            throw new GeometryError("a polygon with no area has no centroid", Map.of("count", count()));
        }
        double cx = 0;
        double cy = 0;
        for (int i = 0; i < vertices.size(); i += 1) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            double factor = a.x() * b.y() - b.x() * a.y();
            cx += (a.x() + b.x()) * factor;
            cy += (a.y() + b.y()) * factor;
        }
        return new Point(cx / (3 * twiceArea), cy / (3 * twiceArea));
    }

    /** The same outline with the vertices running the other way. */
    public Polygon reversed() {
        return new Polygon(vertices.reversed());
    }

    /** The outline running clockwise on screen; this same instance when it already does. */
    public Polygon clockwise() {
        return isClockwise() ? this : reversed();
    }

    /**
     * Crossing number test. A point exactly on an edge is reported as inside, because a mask
     * boundary pixel belongs to the object.
     */
    public boolean contains(Point point) {
        boolean inside = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i, i += 1) {
            Point a = vertices.get(i);
            Point b = vertices.get(j);
            double cross = (b.x() - a.x()) * (point.y() - a.y()) - (b.y() - a.y()) * (point.x() - a.x());
            boolean within = Math.min(a.x(), b.x()) - 1e-9 <= point.x()
                    && point.x() <= Math.max(a.x(), b.x()) + 1e-9
                    && Math.min(a.y(), b.y()) - 1e-9 <= point.y()
                    && point.y() <= Math.max(a.y(), b.y()) + 1e-9;
            if (Math.abs(cross) < 1e-9 && within) {
                return true;
            }
            if ((a.y() > point.y()) != (b.y() > point.y())) {
                double x = ((b.x() - a.x()) * (point.y() - a.y())) / (b.y() - a.y()) + a.x();
                if (point.x() < x) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /**
     * Ramer Douglas Peucker. The outline the mask produces has a vertex per boundary pixel and the
     * quad fitter only wants the corners.
     *
     * @param tolerance in pixels; at zero, or on a triangle, this same instance comes back
     * @return the simplified outline, or this same instance when simplifying would leave fewer than
     *     three vertices
     * @throws BadArgumentError when the tolerance is negative or not finite
     */
    public Polygon simplified(double tolerance) {
        Guards.requireNonNegative(tolerance, "tolerance");
        if (vertices.size() <= 3 || tolerance == 0) {
            return this;
        }
        List<Point> closed = new ArrayList<>(vertices);
        closed.add(vertices.getFirst());
        List<Point> kept = simplifyRun(closed, tolerance);
        kept.removeLast();
        if (kept.size() < 3) {
            return this;
        }
        return new Polygon(kept);
    }

    /** The polygon with every vertex carried through the matrix. */
    public Polygon transformedBy(Matrix matrix) {
        return new Polygon(vertices.stream().map(matrix::apply).toList());
    }

    /** The vertices as {@code {x, y}} maps, in a new list the caller may keep. */
    @Override
    public List<Map<String, Object>> toJson() {
        return vertices.stream().map(Point::toJson).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Polygon polygon && vertices.equals(polygon.vertices);
    }

    @Override
    public int hashCode() {
        return vertices.hashCode();
    }

    /** A summary for a log line, such as {@code polygon(4 vertices, area 5000)}. */
    @Override
    public String toString() {
        return "polygon(" + count() + " vertices, area " + NumberText.of(Num.roundHalfUp(area())) + ")";
    }

    private static double cross(Point o, Point a, Point b) {
        return (a.x() - o.x()) * (b.y() - o.y()) - (a.y() - o.y()) * (b.x() - o.x());
    }

    /** The run with every vertex that sits within the tolerance of the line between its neighbours dropped. */
    private static List<Point> simplifyRun(List<Point> run, double tolerance) {
        if (run.size() < 3) {
            return new ArrayList<>(run);
        }
        Point first = run.getFirst();
        Point last = run.getLast();
        double worst = 0;
        int index = 0;
        for (int i = 1; i < run.size() - 1; i += 1) {
            double distance = perpendicularDistance(run.get(i), first, last);
            if (distance > worst) {
                worst = distance;
                index = i;
            }
        }
        if (worst <= tolerance) {
            return new ArrayList<>(List.of(first, last));
        }
        List<Point> left = simplifyRun(run.subList(0, index + 1), tolerance);
        List<Point> right = simplifyRun(run.subList(index, run.size()), tolerance);
        left.removeLast();
        left.addAll(right);
        return left;
    }

    private static double perpendicularDistance(Point point, Point a, Point b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Num.hypot(dx, dy);
        if (length < 1e-12) {
            return point.distanceTo(a);
        }
        return Math.abs(dy * point.x() - dx * point.y() + b.x() * a.y() - b.y() * a.x()) / length;
    }
}
