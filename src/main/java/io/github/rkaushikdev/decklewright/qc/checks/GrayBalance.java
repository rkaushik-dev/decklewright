package io.github.rkaushikdev.decklewright.qc.checks;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.Patches;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gray balance, read off the step wedge.
 *
 * <p>A neutral patch should come back with its three channels equal. When they are not the capture
 * has a cast, and a cast on a map sheet is the difference between a contour line being brown and
 * being red.
 *
 * <p>It is the worst patch that counts, not the average. A capture that is neutral in the midtones
 * and blue in the shadows has a cast, and averaging hides exactly the patch somebody would complain
 * about.
 */
public final class GrayBalance {
    /** The largest channel difference on any patch, in sample values, per star. */
    public static final List<Rung> GRAY_BALANCE_LADDER =
            List.of(new Rung(4, 3), new Rung(3, 6), new Rung(2, 12), new Rung(1, 24));

    private GrayBalance() {}

    /**
     * How far apart the colour channels are: the largest of the first three less the smallest, so
     * an alpha channel is ignored.
     *
     * @param channels a patch's mean per channel
     * @return in sample values; zero for fewer than three channels
     */
    public static double neutralError(double[] channels) {
        if (channels.length < 3) {
            return 0;
        }
        double high = Math.max(Math.max(channels[0], channels[1]), channels[2]);
        double low = Math.min(Math.min(channels[0], channels[1]), channels[2]);
        return high - low;
    }

    /**
     * The worst neutral error over the target's patches, each read over its inner part, held to the
     * profile's gray balance tolerance. A grayscale capture cannot have a cast and gets four stars.
     * The measurement is in sample values on the eight bit scale.
     */
    public static Check grayBalanceCheck() {
        return Check.of("grayBalance", List.of(Need.SURFACE, Need.TARGET, Need.LIMITS), GrayBalance::measure);
    }

    /**
     * Which way the cast goes, so an operator can fix it rather than just knowing there is one: the
     * channel that sits furthest from the mean of the three, summed over every patch that could be
     * read, and its average distance per patch. On a tie the earlier channel wins.
     *
     * @return empty for a capture with fewer than three channels, or when no patch could be read
     */
    public static Optional<Cast> castDirection(Surface surface, List<Patch> patches) {
        if (surface.channels() < 3) {
            return Optional.empty();
        }
        double[] totals = new double[3];
        int read = 0;

        for (Patch patch : patches) {
            double[] channels = Patches.channelMeans(surface, patch.rect());
            if (channels.length < 3) {
                continue;
            }
            double middle = (channels[0] + channels[1] + channels[2]) / 3;
            for (int c = 0; c < 3; c += 1) {
                totals[c] += channels[c] - middle;
            }
            read += 1;
        }
        if (read == 0) {
            return Optional.empty();
        }

        int at = 0;
        for (int c = 1; c < 3; c += 1) {
            if (Math.abs(totals[c]) > Math.abs(totals[at])) {
                at = c;
            }
        }
        return Optional.of(new Cast(surface.layout().channelNames().get(at), totals[at] / read));
    }

    private static Measurement measure(QcContext context) {
        Surface surface = context.surface().orElseThrow();
        if (surface.channels() < 3) {
            return new Measurement(0, 4, "a grayscale capture cannot have a cast");
        }

        List<Double> errors = new ArrayList<>();
        for (Patch patch : context.target().orElseThrow().patches()) {
            double[] channels = Patches.channelMeans(surface, patch.rect());
            if (channels.length >= 3) {
                errors.add(neutralError(channels));
            }
        }
        if (errors.isEmpty()) {
            return new Measurement(Double.NaN, 0, "no patch of the target could be read");
        }

        double worst = Double.NEGATIVE_INFINITY;
        for (double error : errors) {
            worst = Math.max(worst, error);
        }
        double allowed = context.limits().orElseThrow().grayBalanceTolerance();
        int stars = Check.starsWithin(worst, allowed, GRAY_BALANCE_LADDER, false);
        return new Measurement(
                worst,
                stars,
                "worst patch is " + NumberText.fixed(worst, 1) + " apart across " + NumberText.of(errors.size())
                        + " patches, against " + NumberText.of(allowed));
    }
}
