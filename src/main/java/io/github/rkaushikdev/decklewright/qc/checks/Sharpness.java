package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.starsFor;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Axis;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Along;
import io.github.rkaushikdev.decklewright.raster.Stats;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sharpness, measured across the sheet edge.
 *
 * <p>A proper slanted edge measurement wants a target with a machined edge at a known angle, and the
 * lab does not have one on every stand. What it does have on every capture is the edge of the sheet
 * against the bed, which is a real step of known direction, and the width of the transition across
 * it is a usable stand in.
 *
 * <p>It is reported in pixels of rise, which is what an operator can act on: two pixels means the
 * stand needs refocusing and six means somebody knocked it.
 */
public final class Sharpness {
    /** Rise distance in pixels from a tenth to nine tenths, per star. */
    public static final List<Rung> SHARPNESS_LADDER =
            List.of(new Rung(4, 1.5), new Rung(3, 2.5), new Rung(2, 4), new Rung(1, 7));

    /** The smallest strip, either way, that still has a step worth reading. */
    private static final double THINNEST = 3;

    private Sharpness() {}

    /**
     * The width of the transition in a profile that runs from one flat level to another. Measured
     * between a tenth and nine tenths of the step, which is the usual convention and is what the
     * ladder above is written against. The crossings are interpolated between samples, so a step
     * that happens in one sample reads as eight tenths.
     *
     * @param profile the levels across the edge, in order; left untouched
     * @return in samples, or not a number when there is no step: fewer than three samples, ends
     *     within a billionth of each other, or a level never crossed
     */
    public static double riseDistance(double[] profile) {
        if (profile.length < 3) {
            return Double.NaN;
        }

        double first = profile[0];
        double last = profile[profile.length - 1];
        double step = last - first;
        if (Math.abs(step) < 1e-9) {
            return Double.NaN;
        }

        double low = first + step * 0.1;
        double high = first + step * 0.9;
        boolean rising = step > 0;

        double from = crossing(profile, low, rising);
        double to = crossing(profile, high, rising);
        if (!Double.isFinite(from) || !Double.isFinite(to)) {
            return Double.NaN;
        }
        return Math.abs(to - from);
    }

    /**
     * A strip of capture running across an edge, averaged along it so the noise comes out and the
     * step stays. The strip is clipped to the capture first.
     *
     * @param across the direction the step runs in, so the profile has one value per pixel that way
     * @return the mean of the first channel at each pixel across the edge; empty when what is left
     *     on the capture is thinner than three pixels either way
     */
    public static Optional<double[]> edgeProfile(Surface surface, Rect strip, Axis across) {
        Rect held = strip.intersect(surface.bounds());
        if (held.isEmpty() || held.width() < THINNEST || held.height() < THINNEST) {
            return Optional.empty();
        }
        return Optional.of(
                Stats.meanProfile(new SurfaceView(surface, held), across == Axis.X ? Along.COLUMN : Along.ROW));
    }

    /**
     * The four strips at the usual reach and share.
     *
     * @see #edgeStrips(Rect, StripOptions)
     */
    public static List<EdgeStrip> edgeStrips(Rect rect) {
        return edgeStrips(rect, StripOptions.defaults());
    }

    /**
     * Four strips, one across each side of a rect, reaching a little either way: top, bottom, left
     * and right, each centred on its edge's middle, at least three pixels along it, and rounded to
     * whole pixels half up.
     */
    public static List<EdgeStrip> edgeStrips(Rect rect, StripOptions options) {
        double reach = options.reach();
        double width = Math.max(THINNEST, Num.roundHalfUp(rect.width() * options.along()));
        double height = Math.max(THINNEST, Num.roundHalfUp(rect.height() * options.along()));
        double midX = Num.roundHalfUp(rect.centre().x() - width / 2);
        double midY = Num.roundHalfUp(rect.centre().y() - height / 2);
        return List.of(
                new EdgeStrip(Rect.of(midX, rect.top() - reach, width, reach * 2), Axis.Y),
                new EdgeStrip(Rect.of(midX, rect.bottom() - reach, width, reach * 2), Axis.Y),
                new EdgeStrip(Rect.of(rect.left() - reach, midY, reach * 2, height), Axis.X),
                new EdgeStrip(Rect.of(rect.right() - reach, midY, reach * 2, height), Axis.X));
    }

    /**
     * The check with strips reaching eight pixels either side of each edge.
     *
     * @see #sharpnessCheck(double)
     */
    public static Check sharpnessCheck() {
        return sharpnessCheck(StripOptions.defaults().reach());
    }

    /**
     * The rise distance across the softest of the sheet's four edges that gave a usable step. It is
     * the softest that counts, not the average. The measurement is in pixels.
     *
     * @param reach how far either side of each edge to look, in pixels
     */
    public static Check sharpnessCheck(double reach) {
        return Check.of("sharpness", List.of(Need.SURFACE, Need.SHEET), context -> measure(context, reach));
    }

    private static Measurement measure(QcContext context, double reach) {
        Surface surface = context.surface().orElseThrow();
        List<Double> rises = new ArrayList<>();
        for (EdgeStrip edge : edgeStrips(
                context.sheet().orElseThrow(), StripOptions.defaults().withReach(reach))) {
            Optional<double[]> profile = edgeProfile(surface, edge.strip(), edge.across());
            if (profile.isEmpty()) {
                continue;
            }
            double rise = riseDistance(profile.get());
            if (Double.isFinite(rise)) {
                rises.add(rise);
            }
        }

        if (rises.isEmpty()) {
            return new Measurement(Double.NaN, 0, "no edge of the sheet gave a usable step");
        }

        double worst = Double.NEGATIVE_INFINITY;
        for (double rise : rises) {
            worst = Math.max(worst, rise);
        }
        return new Measurement(
                worst,
                starsFor(worst, SHARPNESS_LADDER),
                "the softest edge rises over " + NumberText.fixed(worst, 2) + " px across "
                        + NumberText.of(rises.size()) + " edges");
    }

    /**
     * Where the profile first passes the level going the step's way, interpolated between the two
     * samples either side; not a number when it never does.
     */
    private static double crossing(double[] values, double level, boolean rising) {
        for (int i = 1; i < values.length; i += 1) {
            double before = values[i - 1];
            double now = values[i];
            boolean passed = rising ? before < level && now >= level : before > level && now <= level;
            if (!passed) {
                continue;
            }
            double span = now - before;
            return span == 0 ? i : i - 1 + (level - before) / span;
        }
        return Double.NaN;
    }
}
