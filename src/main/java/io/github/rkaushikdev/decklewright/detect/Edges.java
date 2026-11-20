package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finding the four sides of a sheet.
 *
 * <p>Scan inward from each edge of the capture and record where the mask first turns on. That
 * gives one point per scan line, and the four sets of points are the four sides. A tear or a dog
 * eared corner puts a few points well off the line, so the fit throws out the worst of them before
 * it commits.
 */
public final class Edges {
    /** The four sides, top then clockwise, in a list that cannot be changed. */
    public static final List<Side> SIDES = List.of(Side.values());

    /** How many times the robust fit trims and refits unless the caller says otherwise. */
    static final int DEFAULT_PASSES = 3;

    /** The share of points the robust fit discards on each pass unless the caller says otherwise. */
    static final double DEFAULT_DROP = 0.15;

    /** Fewer points than this are fitted as they are, with no trimming. */
    private static final int SHORT_RUN = 8;

    private Edges() {}

    /**
     * The first set pixel on every scan line, scanning inward from one side, with nothing trimmed.
     *
     * @see #edgePoints(Mask, Side, int, double)
     */
    public static List<Point> edgePoints(Mask mask, Side side) {
        return edgePoints(mask, side, 1, 0);
    }

    /**
     * The first set pixel on every nth scan line, scanning inward from one side. The top and bottom
     * are scanned down each column, the left and right along each row, and a scan line that holds
     * nothing gives no point.
     *
     * <p>Scanning inward crosses a corner at each end of the run, so the first and last few points
     * belong to the neighbouring side rather than this one. The margin drops a share from each end
     * before anything is fitted, which is far cheaper than trying to fit through them. A run of
     * fewer than eight points is never trimmed.
     *
     * @param step scan every nth line
     * @param margin share dropped from each end of the run
     * @return the points in scan order
     * @throws BadArgumentError when the step is not a whole number above zero, since the scan would
     *     never finish
     */
    public static List<Point> edgePoints(Mask mask, Side side, int step, double margin) {
        Objects.requireNonNull(side, "side");
        Guards.requirePositiveInteger(step, "step");
        List<Point> points = new ArrayList<>();

        if (side == Side.TOP || side == Side.BOTTOM) {
            boolean downward = side == Side.TOP;
            for (int x = 0; x < mask.width(); x += step) {
                for (int k = 0; k < mask.height(); k += 1) {
                    int y = downward ? k : mask.height() - 1 - k;
                    if (mask.get(x, y) != 0) {
                        points.add(new Point(x, y));
                        break;
                    }
                }
            }
            return trimEnds(points, margin);
        }

        boolean rightward = side == Side.LEFT;
        for (int y = 0; y < mask.height(); y += step) {
            for (int k = 0; k < mask.width(); k += 1) {
                int x = rightward ? k : mask.width() - 1 - k;
                if (mask.get(x, y) != 0) {
                    points.add(new Point(x, y));
                    break;
                }
            }
        }
        return trimEnds(points, margin);
    }

    /**
     * Total least squares. An ordinary regression of y on x cannot describe a vertical side, and two
     * of the four sides are vertical.
     *
     * @throws DetectionError when there are fewer than two points
     */
    public static Line fitLine(List<Point> points) {
        if (points.size() < 2) {
            throw new DetectionError("a line needs at least two points", Map.of("count", points.size()));
        }
        Point middle = Point.centroid(points);
        double sxx = 0;
        double syy = 0;
        double sxy = 0;
        for (Point p : points) {
            double dx = p.x() - middle.x();
            double dy = p.y() - middle.y();
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        Angle angle = Angle.radians(0.5 * StrictMath.atan2(2 * sxy, sxx - syy));

        double cos = StrictMath.cos(angle.radians());
        double sin = StrictMath.sin(angle.radians());
        double acc = 0;
        for (Point p : points) {
            double offset = -(p.x() - middle.x()) * sin + (p.y() - middle.y()) * cos;
            acc += offset * offset;
        }
        return new Line(middle, angle, Math.sqrt(acc / points.size()), points);
    }

    /**
     * The signed distance from the line to the point, positive below the line as drawn on screen.
     */
    public static double distanceToLine(Line line, Point point) {
        double cos = StrictMath.cos(line.angle().radians());
        double sin = StrictMath.sin(line.angle().radians());
        return -(point.x() - line.through().x()) * sin
                + (point.y() - line.through().y()) * cos;
    }

    /**
     * Fits, dropping fifteen per cent of the points on each of three passes.
     *
     * @see #fitLineRobust(List, double, int)
     */
    public static Line fitLineRobust(List<Point> points) {
        return fitLineRobust(points, DEFAULT_DROP, DEFAULT_PASSES);
    }

    /**
     * Fit, drop the worst offenders, fit again, and repeat a few times.
     *
     * <p>One pass is not enough and that took a while to accept. The points scanned inward from one
     * side of a turned sheet belong to two edges, not one, because the scan crosses the corner. The
     * wrong edge can be a sixth of the run, and a single trim of a tenth leaves enough of it to pull
     * the angle the wrong way. Three trims converge on the long edge and the residual says so.
     *
     * <p>A run of fewer than eight points, or a drop of zero or less, is fitted once as it is. The
     * trimming stops early when a pass would keep every point, or fewer than two fifths of the run.
     *
     * @param drop share of points to discard each pass
     * @param passes how many times to trim and refit
     * @throws DetectionError when there are fewer than two points
     */
    public static Line fitLineRobust(List<Point> points, double drop, int passes) {
        Line best = fitLine(points);
        if (points.size() < SHORT_RUN || drop <= 0) {
            return best;
        }

        double floor = Math.max(2, Math.ceil(points.size() * 0.4));
        List<Point> current = List.copyOf(points);
        for (int pass = 0; pass < passes; pass += 1) {
            Line line = fitLine(current);
            double[] distances = new double[current.size()];
            for (int i = 0; i < distances.length; i += 1) {
                distances[i] = Math.abs(distanceToLine(line, current.get(i)));
            }
            double cutoff = Math.max(1e-9, quantile(distances, 1 - drop));
            List<Point> kept = new ArrayList<>();
            for (int i = 0; i < distances.length; i += 1) {
                if (distances[i] <= cutoff) {
                    kept.add(current.get(i));
                }
            }
            if (kept.size() < floor || kept.size() == current.size()) {
                best = line;
                break;
            }
            current = kept;
            best = fitLine(current);
        }
        return best;
    }

    /**
     * The four sides, each scanned with the default options and fitted robustly.
     *
     * @see #edgeLines(Mask, EdgeOptions)
     */
    public static SideLines edgeLines(Mask mask) {
        return edgeLines(mask, EdgeOptions.defaults());
    }

    /**
     * The four sides, each scanned inward and fitted robustly.
     *
     * @throws DetectionError when a side gives fewer than two points to fit
     * @throws BadArgumentError when the step is not a whole number above zero
     */
    public static SideLines edgeLines(Mask mask, EdgeOptions options) {
        Objects.requireNonNull(options, "options");
        // Arguments are evaluated in order, so the sides are fitted top first and clockwise, and
        // the first side with nothing to fit is the one reported.
        return new SideLines(
                fitSide(mask, Side.TOP, options),
                fitSide(mask, Side.RIGHT, options),
                fitSide(mask, Side.BOTTOM, options),
                fitSide(mask, Side.LEFT, options));
    }

    /**
     * The skew the four sides agree on. Horizontal sides report their own angle and vertical sides
     * report theirs less a quarter turn, and the median of the four is used rather than the mean so
     * one bad side cannot drag it.
     */
    public static Angle skewFromLines(SideLines lines) {
        double[] readings = {
            lines.top().angle().wrapped().degrees(),
            lines.bottom().angle().wrapped().degrees(),
            normaliseVertical(lines.left().angle().wrapped().degrees()),
            normaliseVertical(lines.right().angle().wrapped().degrees())
        };
        for (int i = 0; i < readings.length; i += 1) {
            readings[i] = ((readings[i] + 45) % 90) - 45;
        }
        return Angle.degrees(Num.median(readings));
    }

    private static Line fitSide(Mask mask, Side side, EdgeOptions options) {
        List<Point> points = edgePoints(mask, side, options.step(), options.margin());
        if (points.size() < 2) {
            throw new DetectionError("the " + side.label() + " side has no edge to fit", Map.of("side", side.label()));
        }
        return fitLineRobust(points, options.drop(), options.passes());
    }

    /**
     * The run with a share cut from each end; nothing is cut from a short run, and a margin that
     * would meet in the middle leaves nothing.
     */
    private static List<Point> trimEnds(List<Point> points, double margin) {
        if (margin <= 0 || points.size() < SHORT_RUN) {
            return points;
        }
        double cut = Math.floor(points.size() * margin);
        if (cut == 0) {
            return points;
        }
        if (!(cut < points.size() - cut)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(points.subList((int) cut, points.size() - (int) cut));
    }

    /** The value at the nearest rank to the fraction, half up; zero for no values. */
    private static double quantile(double[] values, double fraction) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        long rank = Math.round(fraction * (sorted.length - 1));
        int at = (int) Math.min(sorted.length - 1, Math.max(0, rank));
        return sorted[at];
    }

    private static double normaliseVertical(double degrees) {
        return degrees > 0 ? degrees - 90 : degrees + 90;
    }
}
