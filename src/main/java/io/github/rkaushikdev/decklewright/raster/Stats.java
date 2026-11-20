package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sample statistics over a region.
 *
 * <p>The quality checks are built almost entirely out of these. Everything runs in one pass with
 * Welford's update, because a patch on a sixteen bit master is millions of samples and a naive sum
 * of squares loses precision on the flat patches, which are exactly the ones being measured.
 */
public final class Stats {
    private Stats() {}

    /**
     * Minimum, maximum, mean and deviation of one channel.
     *
     * @throws BadArgumentError when the layout has no such channel
     */
    public static ChannelStats channelStats(Raster source, int channel) {
        source.requireChannel(channel);
        int channels = source.channels();
        int count = 0;
        double mean = 0;
        double m2 = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int y = 0; y < source.height(); y += 1) {
            Samples row = source.row(y);
            for (int x = 0; x < source.width(); x += 1) {
                double value = row.get(x * channels + channel);
                count += 1;
                double delta = value - mean;
                mean += delta / count;
                m2 += delta * (value - mean);
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }
        }
        if (count == 0) {
            return new ChannelStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
        }
        return new ChannelStats(min, max, mean, Math.sqrt(m2 / count), count);
    }

    /** The statistics of every channel, in channel order. */
    public static List<ChannelStats> allChannelStats(Raster source) {
        List<ChannelStats> out = new ArrayList<>(source.channels());
        for (int c = 0; c < source.channels(); c += 1) {
            out.add(channelStats(source, c));
        }
        return List.copyOf(out);
    }

    /** The mean of every channel, in channel order. */
    public static double[] meanPixel(Raster source) {
        return allChannelStats(source).stream().mapToDouble(ChannelStats::mean).toArray();
    }

    /**
     * Signal to noise of the first channel.
     *
     * @see #signalToNoise(Raster, int)
     */
    public static double signalToNoise(Raster source) {
        return signalToNoise(source, 0);
    }

    /**
     * Signal to noise in decibels, taken as the mean over the deviation of a patch that should be
     * flat. The imaging guidelines quote it this way and an operator reads the number straight off
     * the report.
     *
     * @return the ratio in decibels; positive infinity for a perfectly flat patch
     * @throws BadArgumentError when the layout has no such channel
     */
    public static double signalToNoise(Raster source, int channel) {
        ChannelStats stats = channelStats(source, channel);
        if (!Double.isFinite(stats.mean())) {
            return Double.NaN;
        }
        if (stats.stdDev() == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return 20 * StrictMath.log10(stats.mean() / stats.stdDev());
    }

    /**
     * How far apart two regions of the same shape are, which is what a regression test on a render
     * compares.
     *
     * @throws BadArgumentError when the extents or channel counts differ
     */
    public static Difference difference(Raster a, Raster b) {
        if (a.width() != b.width() || a.height() != b.height() || a.channels() != b.channels()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("left", shape(a));
            details.put("right", shape(b));
            throw new BadArgumentError("those two regions are not the same shape", details);
        }
        double acc = 0;
        int peak = 0;
        int count = 0;
        for (int y = 0; y < a.height(); y += 1) {
            Samples left = a.row(y);
            Samples right = b.row(y);
            for (int i = 0; i < left.length(); i += 1) {
                double delta = left.get(i) - right.get(i);
                acc += delta * delta;
                int magnitude = (int) Math.abs(delta);
                if (magnitude > peak) {
                    peak = magnitude;
                }
                count += 1;
            }
        }
        return new Difference(count == 0 ? 0 : Math.sqrt(acc / count), peak, count);
    }

    /**
     * One mean per row of the first channel.
     *
     * @see #meanProfile(Raster, Along, int)
     */
    public static double[] meanProfile(Raster source) {
        return meanProfile(source, Along.ROW, 0);
    }

    /**
     * One mean per row or per column of the first channel.
     *
     * @see #meanProfile(Raster, Along, int)
     */
    public static double[] meanProfile(Raster source, Along along) {
        return meanProfile(source, along, 0);
    }

    /**
     * Collapses a region to one number per row or per column, which the projection profiles in the
     * detector are built on.
     *
     * @return the mean of each row, top first, or of each column, left first
     * @throws BadArgumentError when the layout has no such channel
     */
    public static double[] meanProfile(Raster source, Along along, int channel) {
        Objects.requireNonNull(along, "along");
        source.requireChannel(channel);
        int channels = source.channels();
        if (along == Along.ROW) {
            double[] out = new double[source.height()];
            for (int y = 0; y < source.height(); y += 1) {
                Samples row = source.row(y);
                double acc = 0;
                for (int x = 0; x < source.width(); x += 1) {
                    acc += row.get(x * channels + channel);
                }
                out[y] = acc / source.width();
            }
            return out;
        }
        double[] out = new double[source.width()];
        for (int y = 0; y < source.height(); y += 1) {
            Samples row = source.row(y);
            for (int x = 0; x < source.width(); x += 1) {
                out[x] += row.get(x * channels + channel);
            }
        }
        for (int x = 0; x < out.length; x += 1) {
            out[x] /= source.height();
        }
        return out;
    }

    private static Map<String, Object> shape(Raster region) {
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("width", region.width());
        shape.put("height", region.height());
        shape.put("channels", region.channels());
        return shape;
    }
}
