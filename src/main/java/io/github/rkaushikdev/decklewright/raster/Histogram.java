package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A sample distribution.
 *
 * <p>Everything the quality checks say about tone comes from one of these, and so does the bed
 * background estimate. Bin count is a parameter because a mask threshold wants two hundred and
 * fifty six bins regardless of depth, while a clipping check on a sixteen bit master wants the full
 * range. A histogram never changes once built.
 */
public final class Histogram implements JsonValue {
    private final long[] counts;
    private final int maxValue;
    private final long total;

    /**
     * A histogram of prepared counts, which are copied.
     *
     * @param counts how many samples fell in each bin, lowest bin first
     * @param maxValue the top of the sample range the bins cover
     */
    public Histogram(long[] counts, int maxValue) {
        this.counts = counts.clone();
        this.maxValue = maxValue;
        long sum = 0;
        for (long count : this.counts) {
            sum += count;
        }
        this.total = sum;
    }

    /**
     * One bin per sample value of the first channel.
     *
     * @see #histogramOf(Raster, int, int)
     */
    public static Histogram histogramOf(Raster source) {
        return histogramOf(source, 0);
    }

    /**
     * One bin per sample value of one channel.
     *
     * @throws BadArgumentError when the layout has no such channel
     */
    public static Histogram histogramOf(Raster source, int channel) {
        return build(source, channel, source.maxValue() + 1);
    }

    /**
     * The samples of one channel folded into {@code bins} equal bins across the depth's range.
     *
     * @throws BadArgumentError when the layout has no such channel, or the bin count is not above
     *     zero
     */
    public static Histogram histogramOf(Raster source, int channel, int bins) {
        source.requireChannel(channel);
        return build(source, channel, Guards.requirePositiveInteger(bins, "bins"));
    }

    /** One histogram per channel, one bin per sample value, which the gray balance check compares. */
    public static List<Histogram> histogramsOf(Raster source) {
        List<Histogram> out = new ArrayList<>(source.channels());
        for (int c = 0; c < source.channels(); c += 1) {
            out.add(histogramOf(source, c));
        }
        return List.copyOf(out);
    }

    /**
     * One histogram per channel, each with {@code bins} bins.
     *
     * @throws BadArgumentError when the bin count is not above zero
     */
    public static List<Histogram> histogramsOf(Raster source, int bins) {
        List<Histogram> out = new ArrayList<>(source.channels());
        for (int c = 0; c < source.channels(); c += 1) {
            out.add(histogramOf(source, c, bins));
        }
        return List.copyOf(out);
    }

    /** How many bins there are. */
    public int bins() {
        return counts.length;
    }

    /** The top of the sample range the bins cover. */
    public int maxValue() {
        return maxValue;
    }

    /** How many samples were counted. */
    public long total() {
        return total;
    }

    /** How many samples fell in one bin. */
    public long count(int bin) {
        return counts[bin];
    }

    /** The count of every bin, in a new array. */
    public long[] counts() {
        return counts.clone();
    }

    /** The sample value at the centre of a bin, which is what a threshold has to be reported as. */
    public double valueAt(int bin) {
        return ((bin + 0.5) * (maxValue + 1)) / bins();
    }

    /** The bin a sample value falls in, held to the bins there are. */
    public int binFor(double value) {
        double bin = Math.floor((value * bins()) / (maxValue + 1));
        return (int) Num.clamp(bin, 0, bins() - 1);
    }

    /** The mean, taken over the bin centres; not a number when nothing was counted. */
    public double mean() {
        if (total == 0) {
            return Double.NaN;
        }
        double acc = 0;
        for (int i = 0; i < bins(); i += 1) {
            acc += valueAt(i) * counts[i];
        }
        return acc / total;
    }

    /** The bin with the most samples, ties going to the lower. */
    public int modeBin() {
        int best = 0;
        for (int i = 1; i < bins(); i += 1) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return best;
    }

    /** The lowest bin with any samples, or minus one when nothing was counted. */
    public int minBin() {
        for (int i = 0; i < bins(); i += 1) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    /** The highest bin with any samples, or minus one when nothing was counted. */
    public int maxBin() {
        for (int i = bins() - 1; i >= 0; i -= 1) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The sample value at the centre of the first bin that brings the running count up to the
     * fraction of the total.
     *
     * @param fraction between zero and one; anything outside is held to the nearer end
     * @return a sample value, or not a number when nothing was counted
     */
    public double percentile(double fraction) {
        if (total == 0) {
            return Double.NaN;
        }
        double wanted = Num.clamp(fraction, 0, 1) * total;
        long seen = 0;
        for (int i = 0; i < bins(); i += 1) {
            seen += counts[i];
            if (seen >= wanted) {
                return valueAt(i);
            }
        }
        return valueAt(bins() - 1);
    }

    /**
     * The share of samples sitting in the lowest and highest bins, which is what a clipping check
     * reads; both zero when nothing was counted.
     */
    public Clipping clipping() {
        if (total == 0) {
            return new Clipping(0, 0);
        }
        return new Clipping((double) counts[0] / total, (double) counts[bins() - 1] / total);
    }

    /** The running total of the counts, bin by bin. */
    public long[] cumulative() {
        long[] out = new long[bins()];
        long running = 0;
        for (int i = 0; i < bins(); i += 1) {
            running += counts[i];
            out[i] = running;
        }
        return out;
    }

    /**
     * Otsu's method: the sample value that best splits the distribution into two groups, which is
     * how the bed background gets told apart from the sheet without anyone typing a number into the
     * profile.
     *
     * @return the value at the centre of the last bin of the lower group, or not a number when
     *     nothing was counted
     */
    public double otsu() {
        if (total == 0) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = 0; i < bins(); i += 1) {
            sum += (double) i * counts[i];
        }

        double weightLow = 0;
        double sumLow = 0;
        double best = 0;
        int bestBin = 0;
        for (int i = 0; i < bins(); i += 1) {
            weightLow += counts[i];
            if (weightLow == 0) {
                continue;
            }
            double weightHigh = total - weightLow;
            if (weightHigh == 0) {
                break;
            }
            sumLow += (double) i * counts[i];
            double meanLow = sumLow / weightLow;
            double meanHigh = (sum - sumLow) / weightHigh;
            double between = weightLow * weightHigh * StrictMath.pow(meanLow - meanHigh, 2);
            if (between > best) {
                best = between;
                bestBin = i;
            }
        }
        return valueAt(bestBin);
    }

    /** {@code {bins, total, counts}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("bins", (double) bins());
        json.put("total", (double) total);
        json.put(
                "counts",
                Arrays.stream(counts).mapToObj(count -> (double) count).toList());
        return json;
    }

    /**
     * The share of samples in the lowest and in the highest bin.
     *
     * @param low the share in the lowest bin, from zero to one
     * @param high the share in the highest bin, from zero to one
     */
    public record Clipping(double low, double high) {}

    private static Histogram build(Raster source, int channel, int count) {
        source.requireChannel(channel);
        int width = source.maxValue() + 1;
        long[] counts = new long[count];
        double shift = (double) count / width;
        int channels = source.channels();
        for (int y = 0; y < source.height(); y += 1) {
            Samples row = source.row(y);
            for (int x = 0; x < source.width(); x += 1) {
                int value = row.get(x * channels + channel);
                int bin = (int) Math.min(count - 1, Math.floor(value * shift));
                counts[bin] += 1;
            }
        }
        return new Histogram(counts, source.maxValue());
    }
}
