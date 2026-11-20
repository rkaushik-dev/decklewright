package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.Arrays;
import java.util.Map;

/**
 * Skew measured by projecting the outline at candidate angles.
 *
 * <p>Project every set pixel onto the axis across a candidate angle and bin the projections. When
 * the angle is right the sheet's own edges line up and the bins are sharply peaked. When it is
 * wrong the projections smear. The sum of the squared bin counts is the sharpest cheap measure of
 * that, and it is what document scanning has used for forty years.
 *
 * <p>It runs on the outline rather than on the filled mask. A solid rectangle projects to almost
 * the same profile at every small angle, because only the two rows at each end change, so the peak
 * is far too shallow to find. The outline is two thin lines that either line up or do not, and the
 * peak is unmistakable.
 *
 * <p>This exists alongside the caliper fit on purpose. They fail differently: the calipers are
 * thrown by a torn corner, this is thrown by a sheet with a wide pale border, and when the two agree
 * the reading can be trusted.
 */
public final class Deskew {
    private static final int INITIAL_CAPACITY = 256;

    private Deskew() {}

    /**
     * Every pixel of the mask's outline.
     *
     * @see #sampleSetPixels(Mask, int, boolean)
     */
    public static SetPixels sampleSetPixels(Mask mask) {
        return sampleSetPixels(mask, 1, true);
    }

    /**
     * Every nth set pixel of the mask's outline or of its fill, in row by row order.
     *
     * @param step take every nth set pixel; a step of zero takes none
     * @param outline measure the boundary rather than the fill
     * @throws DetectionError when no pixel was taken
     */
    public static SetPixels sampleSetPixels(Mask mask, int step, boolean outline) {
        Mask source = outline ? Morphology.boundary(mask) : mask;
        double[] xs = new double[INITIAL_CAPACITY];
        double[] ys = new double[INITIAL_CAPACITY];
        int taken = 0;
        int seen = 0;
        for (int y = 0; y < source.height(); y += 1) {
            int start = y * source.width();
            for (int x = 0; x < source.width(); x += 1) {
                if (source.data[start + x] == 0) {
                    continue;
                }
                if (step != 0 && seen % step == 0) {
                    if (taken == xs.length) {
                        xs = Arrays.copyOf(xs, taken * 2);
                        ys = Arrays.copyOf(ys, taken * 2);
                    }
                    xs[taken] = x;
                    ys[taken] = y;
                    taken += 1;
                }
                seen += 1;
            }
        }
        if (taken == 0) {
            throw new DetectionError("the mask holds nothing to measure skew from", Map.of());
        }
        return new SetPixels(Arrays.copyOf(xs, taken), Arrays.copyOf(ys, taken));
    }

    /**
     * How sharply the pixels line up across the angle: the mean squared count of the bins their
     * projections fall in. Higher is sharper.
     */
    public static double projectionScore(SetPixels pixels, Angle angle) {
        double cos = StrictMath.cos(angle.radians());
        double sin = StrictMath.sin(angle.radians());
        int count = pixels.count();

        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        double[] projected = new double[count];
        for (int i = 0; i < count; i += 1) {
            double p = -pixels.x(i) * sin + pixels.y(i) * cos;
            projected[i] = p;
            if (p < low) {
                low = p;
            }
            if (p > high) {
                high = p;
            }
        }

        // Splat each projection across the two bins it falls between rather than dropping it in
        // one. Hard binning makes the score jump when a bin edge happens to line up with a row, and
        // the jump is large enough to beat the real peak: a sheet at exactly zero degrees measured as
        // half a degree off for that reason alone.
        int bins = (int) Math.max(2, Math.ceil(high - low) + 2);
        double[] counts = new double[bins];
        for (double value : projected) {
            double at = value - low;
            int bin = (int) Math.min(bins - 2, Math.floor(at));
            double fraction = at - bin;
            counts[bin] += 1 - fraction;
            counts[bin + 1] += fraction;
        }

        double acc = 0;
        for (double binCount : counts) {
            acc += binCount * binCount;
        }
        return acc / count;
    }

    /**
     * The sweep with the default options.
     *
     * @see #coarseSkew(Mask, SkewOptions)
     */
    public static SkewEstimate coarseSkew(Mask mask) {
        return coarseSkew(mask, SkewOptions.defaults());
    }

    /**
     * A sweep across the band, coarse enough to be quick and fine enough that the refinement
     * afterwards has somewhere to start. Candidates run from minus the range up to the range in
     * steps, and the first of equally sharp candidates wins.
     *
     * @throws BadArgumentError when the range or the step is not a finite number above zero
     * @throws DetectionError when the mask holds nothing to measure
     */
    public static SkewEstimate coarseSkew(Mask mask, SkewOptions options) {
        double range = Guards.requirePositive(options.range(), "range");
        double step = Guards.requirePositive(options.step(), "step");
        SetPixels pixels = sampleSetPixels(mask, options.sample(), options.outline());

        double bestAngle = 0;
        double bestScore = -1;
        int tried = 0;
        for (double degrees = -range; degrees <= range + 1e-9; degrees += step) {
            double score = projectionScore(pixels, Angle.degrees(degrees));
            tried += 1;
            if (score > bestScore) {
                bestScore = score;
                bestAngle = degrees;
            }
        }
        return new SkewEstimate(Angle.degrees(bestAngle), bestScore, tried);
    }

    /**
     * The refinement with the default options.
     *
     * @see #refineSkew(Mask, Angle, SkewOptions)
     */
    public static SkewEstimate refineSkew(Mask mask, Angle around) {
        return refineSkew(mask, around, SkewOptions.defaults());
    }

    /**
     * Ternary search inside the window the sweep landed in. The score is not perfectly unimodal at
     * the pixel level, so the search is bounded by a tolerance rather than run to convergence.
     *
     * @param around the angle to search either side of
     * @throws BadArgumentError when the tolerance is not a finite number above zero, since the search
     *     would never stop
     * @throws DetectionError when the mask holds nothing to measure
     */
    public static SkewEstimate refineSkew(Mask mask, Angle around, SkewOptions options) {
        double tolerance = Guards.requirePositive(options.tolerance(), "tolerance");
        SetPixels pixels = sampleSetPixels(mask, options.sample(), options.outline());
        double low = around.degrees() - options.window();
        double high = around.degrees() + options.window();
        int tried = 0;

        while (high - low > tolerance) {
            double third = (high - low) / 3;
            double a = low + third;
            double b = high - third;
            double scoreA = projectionScore(pixels, Angle.degrees(a));
            double scoreB = projectionScore(pixels, Angle.degrees(b));
            tried += 2;
            if (scoreA < scoreB) {
                low = a;
            } else {
                high = b;
            }
        }

        Angle angle = Angle.degrees((low + high) / 2);
        return new SkewEstimate(angle, projectionScore(pixels, angle), tried);
    }

    /**
     * The measurement with the default options.
     *
     * @see #measureSkew(Mask, SkewOptions)
     */
    public static SkewMeasurement measureSkew(Mask mask) {
        return measureSkew(mask, SkewOptions.defaults());
    }

    /**
     * The coarse sweep, then a refinement over one and a half steps either side of its answer.
     *
     * @throws BadArgumentError when the range, the step or the tolerance is not a finite number
     *     above zero
     * @throws DetectionError when the mask holds nothing to measure
     */
    public static SkewMeasurement measureSkew(Mask mask, SkewOptions options) {
        SkewEstimate coarse = coarseSkew(mask, options);
        SkewEstimate fine = refineSkew(mask, coarse.angle(), options.withWindow(options.step() * 1.5));
        return new SkewMeasurement(fine.angle(), coarse.angle(), fine.score(), coarse.tried() + fine.tried());
    }
}
