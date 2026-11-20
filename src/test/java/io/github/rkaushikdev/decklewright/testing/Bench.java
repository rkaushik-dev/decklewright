package io.github.rkaushikdev.decklewright.testing;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A stand in for a capture off the copy stand.
 *
 * <p>A flat bed with a lighter sheet on it, turned by whatever angle is asked for, with optional
 * bed noise, plus {@link #paint} for the extras: a colour target block and a scale bar. Every
 * detection test builds its input from here so the inputs are comparable across the suite.
 *
 * <p>The pixels match the original fixture exactly, seeded noise included, so a test ported from it
 * sees the same capture its expectations were written against.
 */
public final class Bench {
    /** Where the bed noise sequence starts. */
    static final int SEED = 20250508;

    private Bench() {}

    /** The default bench: a 200 by 160 gray capture with a 124 by 88 sheet square in the middle. */
    public static Surface bench() {
        return bench(Options.defaults());
    }

    /** A bench built to the options. */
    public static Surface bench(Options options) {
        Surface surface = Surface.create(options.width(), options.height(), options.layout(), 8, options.bed());
        int channels = surface.channels();
        double cx = options.centreX().orElse(options.width() / 2.0);
        double cy = options.centreY().orElse(options.height() / 2.0);
        double radians = (options.degrees() * Math.PI) / 180;
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        double noise = options.noise();

        int seed = SEED;
        for (int y = 0; y < options.height(); y += 1) {
            for (int x = 0; x < options.width(); x += 1) {
                double dx = x - cx;
                double dy = y - cy;
                double along = dx * cos + dy * sin;
                double across = -dx * sin + dy * cos;
                boolean onSheet = Math.abs(along) <= options.halfWidth() && Math.abs(across) <= options.halfHeight();
                double base = onSheet ? options.paper() : options.bed();
                double jitter = 0;
                if (noise > 0 && !onSheet) {
                    seed = nextSeed(seed);
                    jitter = (seed % (noise * 2 + 1)) - noise;
                }
                double value = Math.max(0, Math.min(255, base + jitter));
                for (int c = 0; c < channels; c += 1) {
                    surface.set(x, y, c, value);
                }
            }
        }
        return surface;
    }

    /**
     * Paints a block of one tone onto a capture, clipped to it.
     *
     * @see #paint(Surface, Rect, Fill)
     */
    public static Surface paint(Surface surface, Rect rect, double value) {
        return paint(surface, rect, Fill.uniform(value));
    }

    /**
     * Paints a block of one colour, a value per channel, onto a capture, clipped to it.
     *
     * @see #paint(Surface, Rect, Fill)
     */
    public static Surface paint(Surface surface, Rect rect, double... values) {
        return paint(surface, rect, Fill.perChannel(values));
    }

    /**
     * Paints a filled block onto a capture, which is how the tests add a colour target or a scale
     * bar to a bench.
     *
     * <p>The block is walked a pixel at a time from its top left corner, clipped to the capture,
     * exactly as the original did. When that corner is not a whole pixel the original wrote each
     * sample at the buffer offset its position worked out to, and only where that offset was a
     * whole number inside the buffer, so a block can paint nothing, or land shifted. That is kept,
     * because the step wedges in the target tests are cut that way.
     *
     * @return the same surface, painted
     * @throws BadArgumentError when a per channel fill does not have one value per channel and the
     *     block covers any of the capture
     */
    public static Surface paint(Surface surface, Rect rect, Fill fill) {
        double[] values = fill.forChannels(surface.channels());
        for (double y = Math.max(0, rect.top()); y < Math.min(surface.height(), rect.bottom()); y += 1) {
            for (double x = Math.max(0, rect.left()); x < Math.min(surface.width(), rect.right()); x += 1) {
                if (x == Math.rint(x) && y == Math.rint(y)) {
                    surface.setPixel((int) x, (int) y, values);
                } else {
                    setAtFractionalPosition(surface, x, y, values);
                }
            }
        }
        return surface;
    }

    /**
     * One step of the bed noise generator. The original multiplied in doubles, which round once the
     * product passes two to the fifty third, before masking to thirty one bits; this does the same
     * so the noise matches sample for sample.
     */
    static int nextSeed(int seed) {
        return (int) (long) (seed * 1103515245.0 + 12345.0) & 0x7fffffff;
    }

    /**
     * What the original's per sample write did for a position that is not a whole pixel: the sample
     * offset {@code (y * width + x) * channels + channel} is worked out in doubles, and a write lands
     * only where it comes out a whole number inside the buffer.
     */
    private static void setAtFractionalPosition(Surface surface, double x, double y, double[] values) {
        int channels = surface.channels();
        if (values.length != channels) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("given", values.length);
            details.put("expected", channels);
            throw new BadArgumentError("that pixel has the wrong number of channels", details);
        }
        Samples data = surface.data();
        double first = (y * surface.width() + x) * channels;
        for (int c = 0; c < channels; c += 1) {
            double index = first + c;
            if (index == Math.rint(index) && index < data.length()) {
                data.set((int) index, values[c]);
            }
        }
    }

    /**
     * What a bench looks like. Every option has the default the detection tests were written
     * against; change one with its {@code withX} method.
     *
     * @param width pixels across the capture
     * @param height pixels down the capture
     * @param bed the tone of the bed
     * @param paper the tone of the sheet
     * @param degrees how far the sheet is turned, clockwise on screen
     * @param halfWidth half the sheet's width, along its own axis
     * @param halfHeight half the sheet's height, along its own axis
     * @param centreX where the sheet's centre sits across; the middle of the capture when empty
     * @param centreY where the sheet's centre sits down; the middle of the capture when empty
     * @param noise bed noise, plus or minus; the sheet is never noisy
     * @param layout the capture's layout; every channel gets the same tone
     */
    public record Options(
            int width,
            int height,
            double bed,
            double paper,
            double degrees,
            double halfWidth,
            double halfHeight,
            OptionalDouble centreX,
            OptionalDouble centreY,
            double noise,
            Layout layout) {
        /** Options of the given parts. */
        public Options {
            Objects.requireNonNull(centreX, "centreX");
            Objects.requireNonNull(centreY, "centreY");
            Objects.requireNonNull(layout, "layout");
        }

        /** A 200 by 160 gray bed of 38 with an upright sheet of 214, 124 by 88, centred, and no noise. */
        public static Options defaults() {
            return new Options(
                    200, 160, 38, 214, 0, 62, 44, OptionalDouble.empty(), OptionalDouble.empty(), 0, Layout.GRAY);
        }

        /** These options with another capture width. */
        public Options withWidth(int width) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with another capture height. */
        public Options withHeight(int height) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with another bed tone. */
        public Options withBed(double bed) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with another sheet tone. */
        public Options withPaper(double paper) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with the sheet turned by another angle. */
        public Options withDegrees(double degrees) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with another half width for the sheet. */
        public Options withHalfWidth(double halfWidth) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with another half height for the sheet. */
        public Options withHalfHeight(double halfHeight) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with the sheet centred at another column. */
        public Options withCentreX(double centreX) {
            return new Options(
                    width,
                    height,
                    bed,
                    paper,
                    degrees,
                    halfWidth,
                    halfHeight,
                    OptionalDouble.of(centreX),
                    centreY,
                    noise,
                    layout);
        }

        /** These options with the sheet centred at another row. */
        public Options withCentreY(double centreY) {
            return new Options(
                    width,
                    height,
                    bed,
                    paper,
                    degrees,
                    halfWidth,
                    halfHeight,
                    centreX,
                    OptionalDouble.of(centreY),
                    noise,
                    layout);
        }

        /** These options with the sheet centred on another point. */
        public Options withCentre(double centreX, double centreY) {
            return withCentreX(centreX).withCentreY(centreY);
        }

        /** These options with another amount of bed noise. */
        public Options withNoise(double noise) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }

        /** These options with another layout. */
        public Options withLayout(Layout layout) {
            return new Options(
                    width, height, bed, paper, degrees, halfWidth, halfHeight, centreX, centreY, noise, layout);
        }
    }
}
