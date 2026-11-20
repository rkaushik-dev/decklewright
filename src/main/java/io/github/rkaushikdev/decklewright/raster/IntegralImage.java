package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summed area tables over one channel: the sums of the samples and of their squares.
 *
 * <p>The bed background estimate walks a window over the whole capture, and the illumination check
 * walks a grid of patches over it again. Without one of these each pass is quadratic in the window
 * size, and on a three hundred megapixel master that is the difference between a second and a lunch
 * break.
 *
 * <p>The tables are one row and one column larger than the source so the corner lookups never need
 * a bounds test. A table never changes once built.
 */
public final class IntegralImage {
    private final double[] sums;
    private final double[] squares;
    private final int width;
    private final int height;

    /**
     * A table over prepared sums, which are copied.
     *
     * @param sums the running sums, {@code (width + 1) * (height + 1)} of them, row by row, with a
     *     leading row and column of zeros
     * @param squares the running sums of squares, laid out the same way
     * @throws BadArgumentError when either table is not the length the extent needs
     */
    public IntegralImage(double[] sums, double[] squares, int width, int height) {
        this(width, height, sums.clone(), squares.clone());
    }

    /** Takes ownership of the two tables. */
    private IntegralImage(int width, int height, double[] sums, double[] squares) {
        long expected = ((long) width + 1) * ((long) height + 1);
        if (sums.length != expected || squares.length != expected) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("sums", sums.length);
            details.put("squares", squares.length);
            details.put("expected", expected);
            throw new BadArgumentError("the tables do not match the stated extent", details);
        }
        this.sums = sums;
        this.squares = squares;
        this.width = width;
        this.height = height;
    }

    /** The tables over the first channel. */
    public static IntegralImage from(Raster source) {
        return from(source, 0);
    }

    /**
     * The tables over one channel.
     *
     * @throws BadArgumentError when the layout has no such channel
     */
    public static IntegralImage from(Raster source, int channel) {
        source.requireChannel(channel);
        int stride = source.width() + 1;
        int cells = Math.multiplyExact(stride, source.height() + 1);
        double[] sums = new double[cells];
        double[] squares = new double[cells];
        int channels = source.channels();

        for (int y = 0; y < source.height(); y += 1) {
            Samples row = source.row(y);
            double runningSum = 0;
            double runningSquare = 0;
            int here = (y + 1) * stride;
            int above = y * stride;
            for (int x = 0; x < source.width(); x += 1) {
                double value = row.get(x * channels + channel);
                runningSum += value;
                runningSquare += value * value;
                sums[here + x + 1] = sums[above + x + 1] + runningSum;
                squares[here + x + 1] = squares[above + x + 1] + runningSquare;
            }
        }
        return new IntegralImage(source.width(), source.height(), sums, squares);
    }

    /** The tables over the first channel. */
    public static IntegralImage integralOf(Raster source) {
        return from(source, 0);
    }

    /**
     * The tables over one channel.
     *
     * @throws BadArgumentError when the layout has no such channel
     */
    public static IntegralImage integralOf(Raster source, int channel) {
        return from(source, channel);
    }

    /** Columns in the source. */
    public int width() {
        return width;
    }

    /** Rows in the source. */
    public int height() {
        return height;
    }

    /**
     * The total of the samples in a window.
     *
     * @throws BadArgumentError when the window is not whole pixels inside the source
     */
    public double sum(Rect rect) {
        requireInside(rect);
        return corners(sums, rect);
    }

    /**
     * The total of the squares of the samples in a window.
     *
     * @throws BadArgumentError when the window is not whole pixels inside the source
     */
    public double sumOfSquares(Rect rect) {
        requireInside(rect);
        return corners(squares, rect);
    }

    /**
     * The mean of a window; not a number for an empty one.
     *
     * @throws BadArgumentError when a window with area is not whole pixels inside the source
     */
    public double mean(Rect rect) {
        double area = rect.area();
        if (area == 0) {
            return Double.NaN;
        }
        return sum(rect) / area;
    }

    /**
     * Mean and deviation of a window in constant time. The deviation comes out of the two tables
     * rather than a second pass, and a tiny negative variance from cancellation is clamped rather
     * than turned into a not a number.
     *
     * @return the summaries, all not a number with a count of zero for an empty window
     * @throws BadArgumentError when a window with area is not whole pixels inside the source
     */
    public Window window(Rect rect) {
        double count = rect.area();
        if (count == 0) {
            return new Window(Double.NaN, Double.NaN, Double.NaN, 0);
        }
        double total = sum(rect);
        double sumOfSquares = sumOfSquares(rect);
        double mean = total / count;
        double variance = Math.max(0, sumOfSquares / count - mean * mean);
        return new Window(mean, variance, Math.sqrt(variance), (int) count);
    }

    /**
     * What {@link #window(Rect)} reports about a window.
     *
     * @param mean the mean sample
     * @param variance the population variance, never below zero
     * @param stdDev the population standard deviation
     * @param count how many samples the window holds
     */
    public record Window(double mean, double variance, double stdDev, int count) {}

    private void requireInside(Rect rect) {
        if (rect.left() < 0
                || rect.top() < 0
                || rect.right() > width
                || rect.bottom() > height
                || !isWhole(rect.left())
                || !isWhole(rect.top())
                || !isWhole(rect.right())
                || !isWhole(rect.bottom())) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rect", rect.toJson());
            details.put("width", width);
            details.put("height", height);
            throw new BadArgumentError("that window is not a whole rect inside the table", details);
        }
    }

    private double corners(double[] table, Rect rect) {
        int stride = width + 1;
        int top = (int) rect.top() * stride;
        int bottom = (int) rect.bottom() * stride;
        int left = (int) rect.left();
        int right = (int) rect.right();
        return table[bottom + right] - table[bottom + left] - table[top + right] + table[top + left];
    }

    private static boolean isWhole(double value) {
        return value == Math.rint(value);
    }
}
