package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.util.Num;
import java.util.List;
import java.util.Objects;

/**
 * Reading a sample at a position that is not a pixel centre.
 *
 * <p>The convention here is that pixel centres sit at whole coordinates, so the sample at x equals
 * two is exactly column two and no interpolation happens. The other convention, with centres at half
 * offsets, shifts a deskewed sheet by half a pixel against its own quality report, which took a week
 * to find.
 *
 * <p>A sampler is bound to one region, so the edge policy is decided once rather than on every one
 * of the hundred million samples a deskew touches. It never changes once made.
 */
public final class Sampler {
    /** Every edge mode, in the order the lab lists them. */
    public static final List<EdgeMode> EDGE_MODES = List.of(EdgeMode.values());

    /** Every sample mode, in the order the lab lists them. */
    public static final List<SampleMode> SAMPLE_MODES = List.of(SampleMode.values());

    private final Raster source;
    private final EdgeMode edge;
    private final double[] background;

    /** A sampler that clamps at the edge, over a black background. */
    public Sampler(Raster source) {
        this(source, EdgeMode.CLAMP, Fill.uniform(0));
    }

    /**
     * A sampler with its own edge policy and background.
     *
     * @param background what a {@linkplain EdgeMode#CONSTANT constant} edge answers; a per channel
     *     background is taken as given, and a channel it has no value for reads as not a number
     */
    public Sampler(Raster source, EdgeMode edge, Fill background) {
        this.source = Objects.requireNonNull(source, "source");
        this.edge = Objects.requireNonNull(edge, "edge");
        this.background = Objects.requireNonNull(background, "background").forChannels(source.channels());
    }

    /** A sampler that clamps at the edge, over a black background. */
    public static Sampler samplerFor(Raster source) {
        return new Sampler(source);
    }

    /** A sampler with its own edge policy and background. */
    public static Sampler samplerFor(Raster source, EdgeMode edge, Fill background) {
        return new Sampler(source, edge, background);
    }

    /**
     * The column or row to read for a position along one axis.
     *
     * @param value the position, which may lie outside
     * @param extent how many columns or rows there are
     * @return the index to read, or minus one when the edge mode says there is none
     */
    public static int resolveIndex(int value, int extent, EdgeMode mode) {
        if (value >= 0 && value < extent) {
            return value;
        }
        return switch (mode) {
            case MIRROR -> {
                if (extent == 1) {
                    yield 0;
                }
                long period = 2L * extent - 2;
                long wrapped = ((value % period) + period) % period;
                yield (int) (wrapped >= extent ? period - wrapped : wrapped);
            }
            case CONSTANT -> -1;
            case CLAMP -> Num.clamp(value, 0, extent - 1);
        };
    }

    /** The region this reads. */
    public Raster source() {
        return source;
    }

    /** What a read past the edge answers. */
    public EdgeMode edge() {
        return edge;
    }

    /** The background a constant edge answers, one value per channel, in a new array. */
    public double[] background() {
        return background.clone();
    }

    /** The sample at a whole pixel, after the edge policy has had its say. */
    public double at(int x, int y, int channel) {
        int sx = resolveIndex(x, source.width(), edge);
        int sy = resolveIndex(y, source.height(), edge);
        if (sx < 0 || sy < 0) {
            return channel < background.length ? background[channel] : Double.NaN;
        }
        return source.get(sx, sy, channel);
    }

    /** The sample of the pixel whose centre is nearest, halves going toward positive infinity. */
    public double nearest(double x, double y, int channel) {
        return at((int) Num.roundHalfUp(x), (int) Num.roundHalfUp(y), channel);
    }

    /** The four pixels around the position, blended; exactly one pixel when the position is a centre. */
    public double bilinear(double x, double y, int channel) {
        double left = Math.floor(x);
        double top = Math.floor(y);
        double fx = x - left;
        double fy = y - top;
        int x0 = (int) left;
        int y0 = (int) top;
        if (fx == 0 && fy == 0) {
            return at(x0, y0, channel);
        }
        double a = at(x0, y0, channel);
        double b = at(x0 + 1, y0, channel);
        double c = at(x0, y0 + 1, channel);
        double d = at(x0 + 1, y0 + 1, channel);
        double upper = a + (b - a) * fx;
        double lower = c + (d - c) * fx;
        return upper + (lower - upper) * fy;
    }

    /** One channel at the position, read {@linkplain SampleMode#BILINEAR bilinearly}. */
    public double sample(double x, double y, int channel) {
        return sample(x, y, channel, SampleMode.BILINEAR);
    }

    /** One channel at the position, read the way the mode says. */
    public double sample(double x, double y, int channel, SampleMode mode) {
        Objects.requireNonNull(mode, "mode");
        return mode == SampleMode.NEAREST ? nearest(x, y, channel) : bilinear(x, y, channel);
    }

    /** Every channel at the position, read {@linkplain SampleMode#BILINEAR bilinearly}. */
    public double[] pixel(double x, double y) {
        return pixel(x, y, SampleMode.BILINEAR);
    }

    /** Every channel at the position, read the way the mode says. */
    public double[] pixel(double x, double y, SampleMode mode) {
        double[] out = new double[source.channels()];
        for (int c = 0; c < out.length; c += 1) {
            out[c] = sample(x, y, c, mode);
        }
        return out;
    }

    /**
     * One channel read {@linkplain SampleMode#BILINEAR bilinearly}, rounded and held to the depth.
     *
     * @see #quantised(double, double, int, SampleMode)
     */
    public int quantised(double x, double y, int channel) {
        return quantised(x, y, channel, SampleMode.BILINEAR);
    }

    /**
     * One channel rounded half up and held to the range of the depth, which is what anything
     * writing back into a surface needs. Kept apart from {@link #sample(double, double, int,
     * SampleMode)} so a measurement can keep the real value. Not a number quantises to zero, which
     * is what storing it would leave.
     */
    public int quantised(double x, double y, int channel, SampleMode mode) {
        double value = sample(x, y, channel, mode);
        return (int) Num.clamp(Num.roundHalfUp(value), 0, source.maxValue());
    }
}
