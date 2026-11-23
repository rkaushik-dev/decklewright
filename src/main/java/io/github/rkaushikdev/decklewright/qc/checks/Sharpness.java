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
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

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
 *
 * <p>The same edges are read a second time for the halo that capture sharpening leaves, because a
 * soft stand with the sharpening turned up reads crisp on rise distance alone.
 */
public final class Sharpness {
    /** Rise distance in pixels from a tenth to nine tenths, per star. */
    public static final List<Rung> SHARPNESS_LADDER =
            List.of(new Rung(4, 1.5), new Rung(3, 2.5), new Rung(2, 4), new Rung(1, 7));

    /** Overshoot past either end of an edge, as a share of the step, per star. */
    public static final List<Rung> HALO_LADDER =
            List.of(new Rung(4, 0.05), new Rung(3, 0.1), new Rung(2, 0.2), new Rung(1, 0.35));

    /** How far apart the colour channels may put an edge, in pixels, per star. */
    public static final List<Rung> REGISTRATION_LADDER =
            List.of(new Rung(4, 0.33), new Rung(3, 0.5), new Rung(2, 0.8), new Rung(1, 1.2));

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
     * A strip of capture running across an edge, on the first channel.
     *
     * @see #edgeProfile(Surface, Rect, Axis, int)
     */
    public static Optional<double[]> edgeProfile(Surface surface, Rect strip, Axis across) {
        return edgeProfile(surface, strip, across, 0);
    }

    /**
     * A strip of capture running across an edge, averaged along it so the noise comes out and the
     * step stays. The strip is clipped to the capture first.
     *
     * @param across the direction the step runs in, so the profile has one value per pixel that way
     * @param channel the channel read
     * @return the mean of the channel at each pixel across the edge; empty when what is left on the
     *     capture is thinner than three pixels either way
     */
    public static Optional<double[]> edgeProfile(Surface surface, Rect strip, Axis across, int channel) {
        Rect held = strip.intersect(surface.bounds());
        if (held.isEmpty() || held.width() < THINNEST || held.height() < THINNEST) {
            return Optional.empty();
        }
        return Optional.of(Stats.meanProfile(
                new SurfaceView(surface, held), across == Axis.X ? Along.COLUMN : Along.ROW, channel));
    }

    /**
     * Where the profile crosses halfway between its two ends, interpolated between samples, which is
     * where a channel puts the edge.
     *
     * @return in samples from the start of the profile; not a number when there is no step
     */
    public static double edgePosition(double[] profile) {
        if (!Double.isFinite(riseDistance(profile))) {
            return Double.NaN;
        }
        double first = profile[0];
        double last = profile[profile.length - 1];
        return crossing(profile, (first + last) / 2, last > first);
    }

    /**
     * How far apart the colour channels put each of the sheet's edges, in pixels: for every edge that
     * gave a usable step on every colour channel, the distance between the earliest and the latest
     * channel's {@linkplain #edgePosition position}.
     *
     * @param reach how far either side of each edge to look, in pixels
     * @return one figure per usable edge, in strip order; empty for a grayscale capture
     */
    public static List<Double> channelShifts(Surface surface, Rect sheet, double reach) {
        int colours = surface.layout().colourCount();
        if (colours < 2) {
            return List.of();
        }
        List<Double> out = new ArrayList<>();
        for (EdgeStrip edge : edgeStrips(sheet, StripOptions.defaults().withReach(reach))) {
            double early = Double.POSITIVE_INFINITY;
            double late = Double.NEGATIVE_INFINITY;
            for (int channel = 0; channel < colours; channel += 1) {
                double at = edgeProfile(surface, edge.strip(), edge.across(), channel)
                        .map(Sharpness::edgePosition)
                        .orElse(Double.NaN);
                early = Math.min(early, at);
                late = Math.max(late, at);
            }
            if (Double.isFinite(early) && Double.isFinite(late)) {
                out.add(late - early);
            }
        }
        return List.copyOf(out);
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

    /**
     * How far the profile overshoots the level it settles at, on either side of the step, as a share
     * of the step: the bright rim and dark trench that capture sharpening draws along every edge. A
     * clean step stays inside its two ends and reads zero.
     *
     * @param profile the levels across the edge, in order; left untouched
     * @return the larger of the overshoot past the far end and the undershoot past the near end, as a
     *     share of the step; not a number when there is no step, as for {@link #riseDistance}
     */
    public static double haloOf(double[] profile) {
        if (!Double.isFinite(riseDistance(profile))) {
            return Double.NaN;
        }
        double first = profile[0];
        double last = profile[profile.length - 1];
        double high = Math.max(first, last);
        double low = Math.min(first, last);
        double over = 0;
        for (double value : profile) {
            over = Math.max(over, Math.max(value - high, low - value));
        }
        return over / (high - low);
    }

    /**
     * The profile across each of the sheet's four edges that came out with a usable step, in the
     * order {@link #edgeStrips(Rect, StripOptions)} gives the strips.
     *
     * @param reach how far either side of each edge to look, in pixels
     */
    public static List<double[]> edgeProfiles(Surface surface, Rect sheet, double reach) {
        List<double[]> out = new ArrayList<>();
        for (EdgeStrip edge : edgeStrips(sheet, StripOptions.defaults().withReach(reach))) {
            edgeProfile(surface, edge.strip(), edge.across())
                    .filter(profile -> Double.isFinite(riseDistance(profile)))
                    .ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    /**
     * The check with strips reaching eight pixels either side of each edge.
     *
     * @see #registrationCheck(double)
     */
    public static Check registrationCheck() {
        return registrationCheck(StripOptions.defaults().reach());
    }

    /**
     * How far apart the colour channels put the sheet's edges, at the worst edge. A lens with lateral
     * colour, or a scanning back whose three passes do not land together, fringes every line on the
     * sheet red on one side and blue on the other. The measurement is in pixels; a grayscale capture
     * has nothing to register and gets four stars.
     *
     * @param reach how far either side of each edge to look, in pixels
     */
    public static Check registrationCheck(double reach) {
        return Check.of(
                "channelRegistration",
                List.of(Need.SURFACE, Need.SHEET),
                context -> measureRegistration(context, reach));
    }

    /**
     * The check with strips reaching eight pixels either side of each edge.
     *
     * @see #sharpeningCheck(double)
     */
    public static Check sharpeningCheck() {
        return sharpeningCheck(StripOptions.defaults().reach());
    }

    /**
     * The halo along the worst of the sheet's edges that gave a usable step. Sharpening in the
     * capture software makes a soft stand pass the sharpness check by drawing a rim along every line
     * on the sheet, and the rim is an artefact the master is not allowed to carry. The measurement is
     * the overshoot as a share of the step.
     *
     * @param reach how far either side of each edge to look, in pixels
     */
    public static Check sharpeningCheck(double reach) {
        return Check.of("sharpening", List.of(Need.SURFACE, Need.SHEET), context -> measureHalo(context, reach));
    }

    private static Measurement measure(QcContext context, double reach) {
        return worstEdge(
                context,
                reach,
                Sharpness::riseDistance,
                (worst, edges) -> new Measurement(
                        worst,
                        starsFor(worst, SHARPNESS_LADDER),
                        "the softest edge rises over " + NumberText.fixed(worst, 2) + " px across "
                                + NumberText.of(edges) + " edges"));
    }

    private static Measurement measureHalo(QcContext context, double reach) {
        return worstEdge(
                context,
                reach,
                Sharpness::haloOf,
                (worst, edges) -> new Measurement(
                        worst,
                        starsFor(worst, HALO_LADDER),
                        worst == 0
                                ? "no halo along " + NumberText.of(edges) + " edges"
                                : "the worst edge overshoots by " + NumberText.fixed(worst * 100, 1)
                                        + " percent of its step across " + NumberText.of(edges) + " edges"));
    }

    private static Measurement measureRegistration(QcContext context, double reach) {
        Surface surface = context.surface().orElseThrow();
        if (surface.layout().colourCount() < 2) {
            return new Measurement(0, Check.MAX_STARS, "a grayscale capture has one channel to register");
        }
        List<Double> shifts = channelShifts(surface, context.sheet().orElseThrow(), reach);
        if (shifts.isEmpty()) {
            return new Measurement(Double.NaN, 0, "no edge of the sheet gave a usable step on every channel");
        }
        double worst = 0;
        for (double shift : shifts) {
            worst = Math.max(worst, shift);
        }
        return new Measurement(
                worst,
                starsFor(worst, REGISTRATION_LADDER),
                "the channels sit up to " + NumberText.fixed(worst, 2) + " px apart across "
                        + NumberText.of(shifts.size()) + " edges");
    }

    /** The worst reading over the usable edges, or a note that there were none. */
    private static Measurement worstEdge(
            QcContext context,
            double reach,
            ToDoubleFunction<double[]> read,
            BiFunction<Double, Integer, Measurement> grade) {
        List<double[]> profiles =
                edgeProfiles(context.surface().orElseThrow(), context.sheet().orElseThrow(), reach);
        if (profiles.isEmpty()) {
            return new Measurement(Double.NaN, 0, "no edge of the sheet gave a usable step");
        }
        double worst = 0;
        for (double[] profile : profiles) {
            worst = Math.max(worst, read.applyAsDouble(profile));
        }
        return grade.apply(worst, profiles.size());
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
