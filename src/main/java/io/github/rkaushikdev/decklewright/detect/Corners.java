package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Quad;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Corners, taken as the crossing of two fitted sides.
 *
 * <p>The minimum area rectangle is a good answer and it is not the best one. It is forced to be a
 * rectangle, so a sheet that is very slightly keystoned gets a corner placed a pixel or two off the
 * paper. Crossing the two lines that were fitted to the sides puts each corner where the paper
 * actually is, and how far that moves it is a useful reading in its own right.
 */
public final class Corners {
    /** The residual, in pixels, at which a side's confidence falls to a half unless the caller says otherwise. */
    private static final double DEFAULT_TOLERANCE = 1;

    /** How far a corner may move, in pixels, unless the caller says otherwise. */
    private static final double DEFAULT_LIMIT = 8;

    private Corners() {}

    /**
     * Where two fitted lines cross.
     *
     * @throws DetectionError when the two are parallel
     */
    public static Point intersect(Line a, Line b) {
        double ca = StrictMath.cos(a.angle().radians());
        double sa = StrictMath.sin(a.angle().radians());
        double cb = StrictMath.cos(b.angle().radians());
        double sb = StrictMath.sin(b.angle().radians());

        // each line is written as its normal dotted with a point equals an offset
        double naX = -sa;
        double naY = ca;
        double nbX = -sb;
        double nbY = cb;
        double oa = naX * a.through().x() + naY * a.through().y();
        double ob = nbX * b.through().x() + nbY * b.through().y();

        double determinant = naX * nbY - naY * nbX;
        if (Math.abs(determinant) < 1e-9) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("first", a.angle().degrees());
            details.put("second", b.angle().degrees());
            throw new DetectionError("those two sides are parallel and never meet", details);
        }
        return new Point((oa * nbY - naY * ob) / determinant, (naX * ob - oa * nbX) / determinant);
    }

    /**
     * The quad whose corners are the crossings of neighbouring sides, put in the usual corner order.
     *
     * @throws DetectionError when two neighbouring sides are parallel
     */
    public static Quad cornersFromLines(SideLines lines) {
        return Quad.ordered(List.of(
                intersect(lines.top(), lines.left()),
                intersect(lines.top(), lines.right()),
                intersect(lines.bottom(), lines.right()),
                intersect(lines.bottom(), lines.left())));
    }

    /**
     * How far each corner moved when the lines were crossed. A sheet that is flat and square moves
     * by well under a pixel. Anything above a couple of pixels means the stand is out of level and
     * the operator needs to know.
     */
    public static CornerShift cornerShift(Quad before, Quad after) {
        List<Point> from = before.corners();
        List<Point> to = after.corners();
        List<Double> moves = new ArrayList<>(from.size());
        double worst = 0;
        double total = 0;
        for (int i = 0; i < from.size(); i += 1) {
            double move = from.get(i).distanceTo(to.get(i));
            moves.add(move);
            worst = Math.max(worst, move);
            total += move;
        }
        return new CornerShift(moves, worst, total / moves.size());
    }

    /**
     * How well a side fits, with confidence a half at a residual of one pixel.
     *
     * @see #lineConfidence(Line, double)
     */
    public static double lineConfidence(Line line) {
        return lineConfidence(line, DEFAULT_TOLERANCE);
    }

    /**
     * How well a fitted side describes the points it was fitted to, turned into a number between
     * zero and one so a report can print it next to the others.
     *
     * @param tolerance the residual at which confidence reaches a half
     * @return one for a perfect fit, falling as the residual grows; zero for a line fitted to fewer
     *     than two points
     */
    public static double lineConfidence(Line line, double tolerance) {
        if (line.points().size() < 2) {
            return 0;
        }
        return 1 / (1 + line.residual() / tolerance);
    }

    /**
     * The weakest of the four sides' confidences, with confidence a half at a residual of one pixel.
     *
     * @see #quadConfidence(SideLines, double)
     */
    public static double quadConfidence(SideLines lines) {
        return quadConfidence(lines, DEFAULT_TOLERANCE);
    }

    /**
     * The weakest of the four sides' {@linkplain #lineConfidence(Line, double) confidences}.
     *
     * @param tolerance the residual at which a side's confidence reaches a half
     */
    public static double quadConfidence(SideLines lines, double tolerance) {
        return Math.min(
                Math.min(lineConfidence(lines.top(), tolerance), lineConfidence(lines.right(), tolerance)),
                Math.min(lineConfidence(lines.bottom(), tolerance), lineConfidence(lines.left(), tolerance)));
    }

    /**
     * Lets each corner move onto the crossing of its sides by up to eight pixels.
     *
     * @see #refineCorners(Quad, SideLines, double)
     */
    public static RefinedCorners refineCorners(Quad quad, SideLines lines) {
        return refineCorners(quad, lines, DEFAULT_LIMIT);
    }

    /**
     * Take the rectangle the calipers produced and let each corner move to where the two fitted
     * sides cross, but only as far as the limit allows. A wild crossing means one of the sides was
     * fitted to something that was not a side, and in that case the rectangle is the better answer.
     * Sides that cannot be crossed at all keep the rectangle too.
     *
     * @param limit how far a corner may move, in pixels
     */
    public static RefinedCorners refineCorners(Quad quad, SideLines lines, double limit) {
        Quad crossed;
        try {
            crossed = cornersFromLines(lines);
        } catch (DeckleError uncrossable) {
            return new RefinedCorners(quad, cornerShift(quad, quad), true);
        }

        CornerShift shift = cornerShift(quad, crossed);
        if (shift.worst() > limit) {
            return new RefinedCorners(quad, shift, true);
        }
        return new RefinedCorners(crossed, shift, false);
    }

    /**
     * The largest distance from any corner to a side it is supposed to sit on. Zero for a quad built
     * from its own lines, and a direct measure of keystone for anything else.
     */
    public static double cornerResidual(Quad quad, SideLines lines) {
        List<Map.Entry<Point, Line>> pairs = List.of(
                Map.entry(quad.topLeft(), lines.top()),
                Map.entry(quad.topRight(), lines.top()),
                Map.entry(quad.topRight(), lines.right()),
                Map.entry(quad.bottomRight(), lines.right()),
                Map.entry(quad.bottomRight(), lines.bottom()),
                Map.entry(quad.bottomLeft(), lines.bottom()),
                Map.entry(quad.bottomLeft(), lines.left()),
                Map.entry(quad.topLeft(), lines.left()));
        double worst = 0;
        for (Map.Entry<Point, Line> pair : pairs) {
            double distance = Math.abs(Edges.distanceToLine(pair.getValue(), pair.getKey()));
            if (distance > worst) {
                worst = distance;
            }
        }
        return worst;
    }
}
