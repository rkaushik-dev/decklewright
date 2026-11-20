package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * What every readable pixel region has in common.
 *
 * <p>The surface and the view had grown two copies of the same dozen accessors, and they had already
 * drifted once, so a measurement over a view answered a different maximum value from the same
 * measurement over its parent. The shared parts live here now and a subclass only has to supply the
 * extent and the three sample accessors.
 *
 * <p>Coordinates are whole pixels with the origin at the top left, and a sample is read back as an
 * unsigned {@code int}. Writing a sample stores it as {@link Samples} does.
 */
public abstract sealed class Raster implements JsonValue permits Surface, SurfaceView {
    private final Layout layout;
    private final int depth;
    private final int channels;
    private final int maxValue;

    /**
     * A region of the given layout and depth.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     */
    Raster(Layout layout, int depth) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.depth = Layouts.requireDepth(depth);
        this.channels = layout.channelCount();
        this.maxValue = Layouts.maxValue(depth);
    }

    /** How the channels of each pixel are laid out. */
    public final Layout layout() {
        return layout;
    }

    /** Bits per sample: eight or sixteen. */
    public final int depth() {
        return depth;
    }

    /** How many samples make up one pixel. */
    public final int channels() {
        return channels;
    }

    /** The largest value a sample can hold at this depth. */
    public final int maxValue() {
        return maxValue;
    }

    /** Pixels across. */
    public abstract int width();

    /** Pixels down. */
    public abstract int height();

    /** The extent. */
    public final Size size() {
        return new Size(width(), height());
    }

    /** Local bounds, always at the origin. */
    public final Rect bounds() {
        return Rect.of(0, 0, width(), height());
    }

    /** How many samples make up one row. */
    public final int rowStride() {
        return width() * channels;
    }

    /** True when the pixel lies inside. */
    public final boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width() && y < height();
    }

    /**
     * Checks that the pixel lies inside.
     *
     * @throws BadArgumentError naming this region when it does not
     */
    final void requireInside(int x, int y) {
        if (!contains(x, y)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("x", x);
            details.put("y", y);
            details.put("width", width());
            details.put("height", height());
            throw new BadArgumentError("that pixel is outside the " + regionName(), details);
        }
    }

    /**
     * The channel, when the layout has it.
     *
     * @throws BadArgumentError when it does not
     */
    final int requireChannel(int channel) {
        if (channel < 0 || channel >= channels) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("channel", channel);
            details.put("channels", channels);
            throw new BadArgumentError("that channel is outside the layout", details);
        }
        return channel;
    }

    /** What the out of range messages call this kind of region. */
    abstract String regionName();

    /**
     * The sample in the first channel.
     *
     * @throws BadArgumentError when the pixel is outside
     */
    public final int get(int x, int y) {
        return get(x, y, 0);
    }

    /**
     * One sample.
     *
     * @throws BadArgumentError when the pixel is outside or the layout has no such channel
     */
    public abstract int get(int x, int y, int channel);

    /**
     * Stores a whole number into one sample, keeping only the bits the depth holds.
     *
     * @throws BadArgumentError when the pixel is outside or the layout has no such channel
     */
    public abstract void set(int x, int y, int channel, int value);

    /**
     * Stores a number into one sample, truncated toward zero and then reduced to the bits the depth
     * holds, as {@link Samples#set(int, double)} stores it.
     *
     * @throws BadArgumentError when the pixel is outside or the layout has no such channel
     */
    public abstract void set(int x, int y, int channel, double value);

    /**
     * Every sample of one pixel, in channel order.
     *
     * @throws BadArgumentError when the pixel is outside
     */
    public final int[] getPixel(int x, int y) {
        int[] out = new int[channels];
        for (int c = 0; c < channels; c += 1) {
            out[c] = get(x, y, c);
        }
        return out;
    }

    /**
     * Stores every sample of one pixel.
     *
     * @throws BadArgumentError when the count of values is not the channel count, or the pixel is
     *     outside
     */
    public final void setPixel(int x, int y, int... values) {
        requirePixelLength(values.length);
        for (int c = 0; c < channels; c += 1) {
            set(x, y, c, values[c]);
        }
    }

    /**
     * Stores every sample of one pixel, each converted as {@link #set(int, int, int, double)}
     * converts it.
     *
     * @throws BadArgumentError when the count of values is not the channel count, or the pixel is
     *     outside
     */
    public final void setPixel(int x, int y, double... values) {
        requirePixelLength(values.length);
        for (int c = 0; c < channels; c += 1) {
            set(x, y, c, values[c]);
        }
    }

    /**
     * A live view of one row's samples, interleaved; writing it writes this region.
     *
     * @throws BadArgumentError when the row is outside
     */
    public abstract Samples row(int y);

    /** Walks the rows top to bottom, each one live, which is how every measurement in the tree is written. */
    public final Iterable<Samples> rows() {
        return () -> IntStream.range(0, height()).mapToObj(this::row).iterator();
    }

    /** {@code {width, height, layout, depth}}, in a new map the caller may keep. */
    @Override
    public final Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("width", (double) width());
        json.put("height", (double) height());
        json.put("layout", layout.label());
        json.put("depth", (double) depth);
        return json;
    }

    /** The geometry, such as {@code 4x3 gray16}. */
    @Override
    public String toString() {
        return width() + "x" + height() + " " + layout + depth;
    }

    private void requirePixelLength(int given) {
        if (given != channels) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("given", given);
            details.put("expected", channels);
            throw new BadArgumentError("that pixel has the wrong number of channels", details);
        }
    }
}
