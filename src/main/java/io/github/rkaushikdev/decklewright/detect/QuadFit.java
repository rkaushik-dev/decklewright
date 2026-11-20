package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.GeometryError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Polygon;
import io.github.rkaushikdev.decklewright.geom.Quad;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rectangle that actually holds the sheet.
 *
 * <p>The projection profiles give an axis aligned box, which is bigger than the sheet whenever the
 * sheet is turned. The minimum area rectangle over the convex hull gives the real one, and its
 * orientation is the skew, measured from the whole outline rather than from four separately fitted
 * sides.
 *
 * <p>Rotating calipers: the minimum area rectangle always has a side flush with a hull edge, so
 * trying every hull edge is enough and there are rarely more than a couple of dozen.
 */
public final class QuadFit {
    private QuadFit() {}

    /** One hull edge's rectangle, in the frame of that edge. */
    private record Candidate(
            double area, double ux, double uy, double minAlong, double maxAlong, double minAcross, double maxAcross) {
        Point toImage(double along, double across) {
            return new Point(along * ux - across * uy, along * uy + across * ux);
        }
    }

    /**
     * The convex hull of every boundary pixel of the mask.
     *
     * @see #outlineHull(Mask, int)
     */
    public static Polygon outlineHull(Mask mask) {
        return outlineHull(mask, 1);
    }

    /**
     * The convex hull of every nth boundary pixel of the mask, in row by row order.
     *
     * @param step take every nth boundary pixel; a step of zero takes none
     * @throws DetectionError when fewer than three boundary pixels were taken
     * @throws GeometryError when every pixel taken lies on one line
     */
    public static Polygon outlineHull(Mask mask, int step) {
        Mask edge = Morphology.boundary(mask);
        List<Point> points = new ArrayList<>();
        int seen = 0;
        for (int y = 0; y < edge.height(); y += 1) {
            int start = y * edge.width();
            for (int x = 0; x < edge.width(); x += 1) {
                if (edge.data[start + x] == 0) {
                    continue;
                }
                if (step != 0 && seen % step == 0) {
                    points.add(new Point(x, y));
                }
                seen += 1;
            }
        }
        if (points.size() < 3) {
            throw new DetectionError("there is no outline to fit a quad to", Map.of("points", points.size()));
        }
        return Polygon.convexHull(points);
    }

    /**
     * The minimum area rectangle over a hull, found by trying a rectangle flush with every hull
     * edge; the first of equally small ones wins.
     *
     * @throws DetectionError when every hull edge has no length
     */
    public static CaliperRect minimumAreaRect(Polygon hull) {
        List<Point> vertices = hull.vertices();
        Candidate best = null;

        for (int i = 0; i < vertices.size(); i += 1) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double length = Num.hypot(dx, dy);
            if (length < 1e-9) {
                continue;
            }
            double ux = dx / length;
            double uy = dy / length;

            double minAlong = Double.POSITIVE_INFINITY;
            double maxAlong = Double.NEGATIVE_INFINITY;
            double minAcross = Double.POSITIVE_INFINITY;
            double maxAcross = Double.NEGATIVE_INFINITY;
            for (Point p : vertices) {
                double along = p.x() * ux + p.y() * uy;
                double across = -p.x() * uy + p.y() * ux;
                if (along < minAlong) {
                    minAlong = along;
                }
                if (along > maxAlong) {
                    maxAlong = along;
                }
                if (across < minAcross) {
                    minAcross = across;
                }
                if (across > maxAcross) {
                    maxAcross = across;
                }
            }

            double area = (maxAlong - minAlong) * (maxAcross - minAcross);
            if (best == null || area < best.area()) {
                best = new Candidate(area, ux, uy, minAlong, maxAlong, minAcross, maxAcross);
            }
        }

        if (best == null) {
            throw new DetectionError("the hull has no usable edge", Map.of("vertices", vertices.size()));
        }

        List<Point> corners = List.of(
                best.toImage(best.minAlong(), best.minAcross()),
                best.toImage(best.maxAlong(), best.minAcross()),
                best.toImage(best.maxAlong(), best.maxAcross()),
                best.toImage(best.minAlong(), best.maxAcross()));
        return new CaliperRect(
                Quad.ordered(corners), foldToBand(Angle.radians(StrictMath.atan2(best.uy(), best.ux()))), best.area());
    }

    /**
     * A rectangle has four equally good orientations, so the reported skew is folded into the band
     * from minus forty five up to and including forty five. Anything wider than that is a quarter
     * turn, which is a separate decision the work order makes rather than something the detector
     * guesses.
     */
    public static Angle foldToBand(Angle angle) {
        double wrapped = angle.wrapped().degrees();
        double folded = ((((wrapped + 45) % 90) + 90) % 90) - 45;
        return Angle.degrees(folded == -45 ? 45 : folded);
    }

    /**
     * The rectangle holding the mask, fitted to every boundary pixel.
     *
     * @see #fitQuad(Mask, int)
     */
    public static FittedQuad fitQuad(Mask mask) {
        return fitQuad(mask, 1);
    }

    /**
     * The minimum area rectangle over the hull of the mask's outline, with its skew and how well the
     * mask fills it.
     *
     * @param step take every nth boundary pixel
     * @throws DetectionError when the mask has no outline to fit
     * @throws GeometryError when the outline lies on one line
     */
    public static FittedQuad fitQuad(Mask mask, int step) {
        Polygon hull = outlineHull(mask, step);
        CaliperRect rect = minimumAreaRect(hull);
        long covered = mask.count();
        return new FittedQuad(rect.quad(), quadSkew(rect.quad()), hull, rect.area() == 0 ? 0 : covered / rect.area());
    }

    /**
     * The orientation of a quad, taken from its longer pair of sides, folded into the same band as
     * everything else here. A square takes its top side.
     */
    public static Angle quadSkew(Quad quad) {
        double[] lengths = quad.edgeLengths();
        double horizontal = (lengths[0] + lengths[2]) / 2;
        double vertical = (lengths[1] + lengths[3]) / 2;
        List<Angle> angles = quad.edgeAngles();
        Angle chosen = horizontal >= vertical ? angles.get(0) : angles.get(1);
        return foldToBand(chosen);
    }
}
