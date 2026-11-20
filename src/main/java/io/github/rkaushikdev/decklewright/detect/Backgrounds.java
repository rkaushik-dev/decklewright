package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.ChannelStats;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Stats;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What the bed looks like, and what on it stands out.
 *
 * <p>Every stand in the lab has a matte board behind the sheet and every board is a different
 * grey. Asking the profile to name the colour was tried and it went stale within a month of the
 * boards being replaced, so the estimate is taken from the capture itself, off the four border
 * strips where a correctly placed sheet never reaches.
 */
public final class Backgrounds {
    /** How much of each edge is read as bed unless the caller says otherwise. */
    static final double DEFAULT_FRACTION = 0.04;

    /** How many spreads from the bed a pixel must be to count as object unless the caller says otherwise. */
    static final double DEFAULT_SIGMA = 6;

    /** The largest spread, as a share of full scale, a bed may have and still be uniform. */
    static final double DEFAULT_MAX_SPREAD = 0.06;

    /** Full scale of an eight bit capture. */
    static final double DEFAULT_FULL_SCALE = 255;

    private Backgrounds() {}

    /**
     * The four strips along the edges of the capture that are read as bed: the top and bottom
     * across the whole width, then the left and right between them. None is thinner than a pixel.
     *
     * @param fraction how much of each edge counts as border
     * @throws BadArgumentError when the fraction is outside 0.001 to 0.45
     */
    public static List<Rect> borderStrips(Surface surface, double fraction) {
        Guards.requireInRange(fraction, 0.001, 0.45, "fraction");
        double width = surface.width();
        double height = surface.height();
        double thickX = Math.max(1, Num.roundHalfUp(width * fraction));
        double thickY = Math.max(1, Num.roundHalfUp(height * fraction));
        double between = Math.max(1, height - 2 * thickY);
        return List.of(
                Rect.of(0, 0, width, thickY),
                Rect.of(0, height - thickY, width, thickY),
                Rect.of(0, thickY, thickX, between),
                Rect.of(width - thickX, thickY, thickX, between));
    }

    /**
     * The bed, read off the default four per cent of each edge.
     *
     * @see #estimateBackground(Surface, double)
     */
    public static Background estimateBackground(Surface surface) {
        return estimateBackground(surface, DEFAULT_FRACTION);
    }

    /**
     * The bed's tone and spread per channel, pooled over every border strip that lies on the
     * capture.
     *
     * @param fraction how much of each edge counts as border
     * @throws BadArgumentError when the fraction is outside 0.001 to 0.45
     * @throws DetectionError when no strip lies on the capture
     */
    public static Background estimateBackground(Surface surface, double fraction) {
        List<Rect> strips = borderStrips(surface, fraction).stream()
                .filter(surface.bounds()::containsRect)
                .toList();
        if (strips.isEmpty()) {
            throw new DetectionError(
                    "the capture is too small to have a border",
                    Map.of("size", surface.size().toJson()));
        }

        int channels = surface.channels();
        double[] means = new double[channels];
        double[] variances = new double[channels];
        int samples = 0;

        for (Rect rect : strips) {
            List<ChannelStats> stats = Stats.allChannelStats(new SurfaceView(surface, rect));
            int weight = stats.get(0).count();
            samples += weight;
            for (int c = 0; c < channels; c += 1) {
                ChannelStats channel = stats.get(c);
                means[c] += channel.mean() * weight;
                variances[c] += (StrictMath.pow(channel.stdDev(), 2) + StrictMath.pow(channel.mean(), 2)) * weight;
            }
        }

        double[] mean = new double[channels];
        double[] stdDev = new double[channels];
        for (int c = 0; c < channels; c += 1) {
            mean[c] = means[c] / samples;
        }
        for (int c = 0; c < channels; c += 1) {
            stdDev[c] = Math.sqrt(Math.max(0, variances[c] / samples - StrictMath.pow(mean[c], 2)));
        }
        return new Background(boxed(mean), boxed(stdDev), samples);
    }

    /**
     * How far a pixel sits from the background, in units of the background's own spread. A flat bed
     * gives a small spread and a textured board gives a large one, so the same threshold works on
     * both. A spread below one is taken as one, and a channel the background does not describe is
     * not compared.
     *
     * @param pixel one value per channel
     * @return the distance of the worst channel
     */
    public static double distanceFrom(double[] pixel, Background background) {
        List<Double> mean = background.mean();
        List<Double> stdDev = background.stdDev();
        int compared = Math.min(pixel.length, Math.min(mean.size(), stdDev.size()));
        double worst = 0;
        for (int c = 0; c < compared; c += 1) {
            double spread = Math.max(1, stdDev.get(c));
            double distance = Math.abs(pixel[c] - mean.get(c)) / spread;
            if (distance > worst) {
                worst = distance;
            }
        }
        return worst;
    }

    /**
     * Everything more than six spreads from the bed, with the bed estimated off the default border.
     *
     * @see #objectMask(Surface, double, double)
     */
    public static ObjectMask objectMask(Surface surface) {
        return objectMask(surface, DEFAULT_SIGMA, DEFAULT_FRACTION);
    }

    /**
     * Everything more than {@code sigma} spreads from the bed, with the bed estimated off the
     * default border.
     *
     * @see #objectMask(Surface, double, double)
     */
    public static ObjectMask objectMask(Surface surface, double sigma) {
        return objectMask(surface, sigma, DEFAULT_FRACTION);
    }

    /**
     * Everything that stands out from the bed, with the bed estimated from the capture first.
     *
     * @param sigma how many spreads away from the bed counts as object
     * @param fraction how much of each edge is read as bed
     * @throws BadArgumentError when the fraction is outside 0.001 to 0.45
     * @throws DetectionError when the capture has no border to read
     */
    public static ObjectMask objectMask(Surface surface, double sigma, double fraction) {
        return thresholdAgainst(surface, estimateBackground(surface, fraction), sigma);
    }

    /**
     * Everything that stands out from a bed that was already measured, which comes back in the
     * result as the very same instance.
     *
     * @param sigma how many spreads away from the bed counts as object
     */
    public static ObjectMask objectMask(Surface surface, Background background, double sigma) {
        return thresholdAgainst(surface, Objects.requireNonNull(background, "background"), sigma);
    }

    /**
     * True when the bed is uniform enough for the estimate to mean anything, judged against eight
     * bit full scale.
     *
     * @see #isUniform(Background, double, double)
     */
    public static boolean isUniform(Background background) {
        return isUniform(background, DEFAULT_MAX_SPREAD, DEFAULT_FULL_SCALE);
    }

    /**
     * True when the bed is uniform enough for the estimate to mean anything. A board with a shadow
     * across it fails this and the operator gets told to relight rather than getting a crop that is
     * quietly wrong.
     *
     * @param maxSpread the largest spread allowed on any channel, as a share of full scale
     * @param fullScale the largest sample value at the capture's depth
     */
    public static boolean isUniform(Background background, double maxSpread, double fullScale) {
        return background.stdDev().stream().allMatch(value -> value / fullScale <= maxSpread);
    }

    /** One line for the run log, such as {@code bed at 40, 41 give or take 3.3, 3.0 over 1200 samples}. */
    public static String describeBackground(Background background) {
        String mean = background.mean().stream()
                .map(value -> NumberText.of(Num.roundHalfUp(value)))
                .collect(Collectors.joining(", "));
        String spread = background.stdDev().stream()
                .map(value -> NumberText.fixed(value, 1))
                .collect(Collectors.joining(", "));
        return "bed at " + mean + " give or take " + spread + " over " + background.samples() + " samples";
    }

    private static ObjectMask thresholdAgainst(Surface surface, Background bed, double sigma) {
        Mask mask = new Mask(surface.width(), surface.height());
        int channels = surface.channels();
        double[] mean = bed.mean().stream().mapToDouble(Double::doubleValue).toArray();
        double[] spread =
                bed.stdDev().stream().mapToDouble(value -> Math.max(1, value)).toArray();
        // A channel the background does not describe never counts.
        int compared = Math.min(channels, Math.min(mean.length, spread.length));

        int at = 0;
        for (int y = 0; y < surface.height(); y += 1) {
            Samples row = surface.row(y);
            for (int x = 0; x < surface.width(); x += 1) {
                int base = x * channels;
                double worst = 0;
                for (int c = 0; c < compared; c += 1) {
                    double distance = Math.abs(row.get(base + c) - mean[c]) / spread[c];
                    if (distance > worst) {
                        worst = distance;
                    }
                }
                mask.data[at] = (byte) (worst > sigma ? 1 : 0);
                at += 1;
            }
        }
        return new ObjectMask(mask, bed);
    }

    private static List<Double> boxed(double[] values) {
        return Arrays.stream(values).boxed().toList();
    }
}
