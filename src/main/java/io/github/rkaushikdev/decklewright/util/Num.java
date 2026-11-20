package io.github.rkaushikdev.decklewright.util;

import java.util.Arrays;
import java.util.Collection;

/**
 * Small numeric helpers.
 *
 * <p>Rounding shows up everywhere in this tree and the lab cares which way it goes, so nothing
 * here rounds half up by accident: {@link #roundHalfEven(double)} is the default, and each helper
 * says which way it goes.
 */
public final class Num {
    /** Distance below which two measurements are treated as the same. */
    public static final double DEFAULT_EPSILON = 1e-9;

    private Num() {}

    /** The value held inside {@code [low, high]}. */
    public static double clamp(double value, double low, double high) {
        if (value < low) {
            return low;
        }
        if (value > high) {
            return high;
        }
        return value;
    }

    /** The value held inside {@code [low, high]}. */
    public static int clamp(int value, int low, int high) {
        if (value < low) {
            return low;
        }
        if (value > high) {
            return high;
        }
        return value;
    }

    /** Linear interpolation from {@code a} at {@code t = 0} to {@code b} at {@code t = 1}; extrapolates outside. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** True when the two are no further apart than {@link #DEFAULT_EPSILON}. */
    public static boolean approxEqual(double a, double b) {
        return approxEqual(a, b, DEFAULT_EPSILON);
    }

    /** True when the two are no further apart than {@code epsilon}. */
    public static boolean approxEqual(double a, double b, double epsilon) {
        return Math.abs(a - b) <= epsilon;
    }

    /**
     * Banker's rounding. Half up biases every derivative a fraction of a pixel larger, which shows
     * up as a systematic drift once you stack four operations.
     */
    public static double roundHalfEven(double value) {
        double floor = Math.floor(value);
        double diff = value - floor;
        if (diff > 0.5) {
            return floor + 1;
        }
        if (diff < 0.5) {
            return floor;
        }
        return floor % 2 == 0 ? floor : floor + 1;
    }

    /**
     * The nearest whole number with halves going towards positive infinity, for the figures that
     * are not rounded with {@link #roundHalfEven(double)}.
     *
     * <p>Unlike {@link Math#round(double)} this stays a double and keeps the sign of a zero, so
     * minus a quarter rounds to minus zero, and a value too large to have a fraction comes back
     * unchanged rather than clamped to the range of a long. Not a number stays not a number.
     */
    public static double roundHalfUp(double value) {
        if (!(Math.abs(value) < 0x1p52)) {
            return value;
        }
        return Math.copySign((double) Math.round(value), value);
    }

    /** The nearest multiple of {@code step}, halves to the even multiple. */
    public static double roundToMultiple(double value, double step) {
        Guards.requirePositive(step, "step");
        return roundHalfEven(value / step) * step;
    }

    /** The multiple of {@code step} at or below the value. */
    public static double floorToMultiple(double value, double step) {
        Guards.requirePositive(step, "step");
        return Math.floor(value / step) * step;
    }

    /** The multiple of {@code step} at or above the value. */
    public static double ceilToMultiple(double value, double step) {
        Guards.requirePositive(step, "step");
        return Math.ceil(value / step) * step;
    }

    /** The value rounded half even to {@code places} decimal places; a negative count rounds to tens, hundreds and so on. */
    public static double roundTo(double value, int places) {
        double scale = StrictMath.pow(10, places);
        return roundHalfEven(value * scale) / scale;
    }

    /** The total, added in order; zero for no values. */
    public static double sum(double... values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    /** The total, added in order; zero for no values. */
    public static double sum(Collection<? extends Number> values) {
        return sum(unbox(values));
    }

    /** The arithmetic mean, or not a number for no values. */
    public static double mean(double... values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        return sum(values) / values.length;
    }

    /** The arithmetic mean, or not a number for no values. */
    public static double mean(Collection<? extends Number> values) {
        return mean(unbox(values));
    }

    /**
     * Linear interpolation between the bracketing samples, which is what the imaging guidelines
     * assume when they quote a percentile. The input is left untouched.
     *
     * @param fraction between 0 and 1; anything outside is held to the nearer end
     * @return the percentile, or not a number for no values
     */
    public static double percentile(double[] values, double fraction) {
        if (values.length == 0) {
            return Double.NaN;
        }
        Guards.requireFinite(fraction, "fraction");
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double position = clamp(fraction, 0, 1) * (sorted.length - 1);
        int low = (int) Math.floor(position);
        int high = (int) Math.ceil(position);
        if (low == high) {
            return sorted[low];
        }
        return lerp(sorted[low], sorted[high], position - low);
    }

    /** As {@link #percentile(double[], double)}. */
    public static double percentile(Collection<? extends Number> values, double fraction) {
        return percentile(unbox(values), fraction);
    }

    /** The fiftieth percentile, or not a number for no values. */
    public static double median(double... values) {
        return percentile(values, 0.5);
    }

    /** The fiftieth percentile, or not a number for no values. */
    public static double median(Collection<? extends Number> values) {
        return median(unbox(values));
    }

    /**
     * Population standard deviation. Sample deviation is never what we want here because the
     * pixels are the whole population of the patch.
     *
     * @return the deviation, or not a number for no values
     */
    public static double stdDev(double... values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double m = mean(values);
        double acc = 0;
        for (double value : values) {
            acc += (value - m) * (value - m);
        }
        return Math.sqrt(acc / values.length);
    }

    /** As {@link #stdDev(double...)}. */
    public static double stdDev(Collection<? extends Number> values) {
        return stdDev(unbox(values));
    }

    /**
     * The length of the vector {@code (x, y)}, scaled by the larger magnitude before the squares
     * are summed: both magnitudes are divided by the larger one, the squares are summed, and the
     * root is scaled back up. Dividing first keeps the squares from overflowing.
     *
     * <p>{@link StrictMath#hypot(double, double)} rounds differently in the last place for about a
     * third of ordinary inputs, and edge lengths and corner shifts are compared exactly, so nothing
     * in the tree uses it.
     *
     * @return positive infinity when either is infinite, otherwise not a number when either is not
     *     a number, otherwise the length, which is never negative
     */
    public static double hypot(double x, double y) {
        if (Double.isInfinite(x) || Double.isInfinite(y)) {
            return Double.POSITIVE_INFINITY;
        }
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return Double.NaN;
        }
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double max = Math.max(ax, ay);
        if (max == 0) {
            return 0;
        }
        double nx = ax / max;
        double ny = ay / max;
        return Math.sqrt(nx * nx + ny * ny) * max;
    }

    /** The greatest common divisor of the whole parts of the two magnitudes. */
    public static double gcd(double a, double b) {
        double x = Math.abs(truncate(a));
        double y = Math.abs(truncate(b));
        while (y != 0) {
            double t = y;
            y = x % y;
            x = t;
        }
        return x;
    }

    /** True for a whole even number, within {@link #DEFAULT_EPSILON}. */
    public static boolean isEven(double value) {
        return Math.abs(value % 2) < DEFAULT_EPSILON;
    }

    /** The whole part, rounding toward zero. */
    public static double truncate(double value) {
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }

    private static double[] unbox(Collection<? extends Number> values) {
        double[] out = new double[values.size()];
        int i = 0;
        for (Number value : values) {
            out[i] = value.doubleValue();
            i += 1;
        }
        return out;
    }
}
