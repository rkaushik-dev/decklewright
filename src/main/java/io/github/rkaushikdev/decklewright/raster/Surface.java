package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A pixel buffer.
 *
 * <p>Samples are interleaved, rows run top to bottom and there is no padding between them. Every
 * capture the lab produces is small enough to hold whole, because a twenty four by thirty six inch
 * sheet at six hundred ppi is only about three hundred megapixels and the machines have the memory.
 * Eight bit surfaces keep one byte a sample and sixteen bit surfaces two, in {@link Samples}.
 *
 * <p>The shape of a surface never changes; its samples are written in place.
 */
public final class Surface extends Raster {
    /** The most samples one buffer can hold, which is where Java arrays stop. */
    static final int MAX_SAMPLES = Integer.MAX_VALUE - 8;

    private final int width;
    private final int height;
    private final Samples data;

    private Surface(Samples data, int width, int height, Layout layout, int depth) {
        super(layout, depth);
        this.width = Guards.requirePositiveInteger(width, "width");
        this.height = Guards.requirePositiveInteger(height, "height");
        Objects.requireNonNull(data, "data");

        long expected = (long) width * height * channels();
        if (data.length() != expected) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("given", data.length());
            details.put("expected", expected);
            throw new BadArgumentError("the buffer does not match the stated geometry", details);
        }
        Class<? extends Samples> type = Layouts.arrayTypeFor(depth);
        if (!type.isInstance(data)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("depth", depth);
            details.put("got", data.getClass().getSimpleName());
            throw new BadArgumentError("a " + depth + " bit surface needs " + type.getSimpleName(), details);
        }
        this.data = data;
    }

    /**
     * A black eight bit colour surface.
     *
     * @throws BadArgumentError when an extent is not above zero
     * @throws UnsupportedError when the surface holds more samples than one buffer can
     */
    public static Surface create(int width, int height) {
        return create(width, height, Layout.RGB, 8, 0);
    }

    /**
     * A black eight bit surface of the layout.
     *
     * @throws BadArgumentError when an extent is not above zero
     * @throws UnsupportedError when the surface holds more samples than one buffer can
     */
    public static Surface create(int width, int height, Layout layout) {
        return create(width, height, layout, 8, 0);
    }

    /**
     * A black surface of the layout and depth.
     *
     * @throws BadArgumentError when an extent is not above zero, or the depth is not one the lab
     *     works in
     * @throws UnsupportedError when the surface holds more samples than one buffer can
     */
    public static Surface create(int width, int height, Layout layout, int depth) {
        return create(width, height, layout, depth, 0);
    }

    /**
     * A surface of the layout and depth with every sample set to {@code fill}, stored as {@link
     * Samples#set(int, double)} stores it.
     *
     * @throws BadArgumentError when an extent is not above zero, or the depth is not one the lab
     *     works in
     * @throws UnsupportedError when the surface holds more samples than one buffer can
     */
    public static Surface create(int width, int height, Layout layout, int depth, double fill) {
        Objects.requireNonNull(layout, "layout");
        Layouts.requireDepth(depth);
        Guards.requirePositiveInteger(width, "width");
        Guards.requirePositiveInteger(height, "height");
        Samples data = Samples.allocate(depth, sampleCount(width, height, layout.channelCount()));
        if (fill != 0) {
            data.fill(fill);
        }
        return new Surface(data, width, height, layout, depth);
    }

    /**
     * A surface over samples that already exist, such as a codec's output, without copying them.
     *
     * @throws BadArgumentError when an extent is not above zero, the depth is not one the lab works
     *     in, the buffer's length does not match the geometry, or its storage does not match the
     *     depth
     */
    public static Surface wrap(Samples data, int width, int height, Layout layout, int depth) {
        return new Surface(data, width, height, layout, depth);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    /** The whole buffer, live. */
    public Samples data() {
        return data;
    }

    /** How many samples the buffer holds. */
    public int sampleCount() {
        return data.length();
    }

    @Override
    String regionName() {
        return "surface";
    }

    /**
     * Where the first channel of the pixel sits in {@link #data()}.
     *
     * @throws BadArgumentError when the pixel is outside
     */
    public int indexOf(int x, int y) {
        return indexOf(x, y, 0);
    }

    /**
     * Where one sample sits in {@link #data()}.
     *
     * @throws BadArgumentError when the pixel is outside or the layout has no such channel
     */
    public int indexOf(int x, int y, int channel) {
        requireInside(x, y);
        requireChannel(channel);
        return (y * width + x) * channels() + channel;
    }

    @Override
    public int get(int x, int y, int channel) {
        return data.get(indexOf(x, y, channel));
    }

    @Override
    public void set(int x, int y, int channel, int value) {
        data.set(indexOf(x, y, channel), value);
    }

    @Override
    public void set(int x, int y, int channel, double value) {
        data.set(indexOf(x, y, channel), value);
    }

    /** A live view of one row, so a codec can write straight into it. */
    @Override
    public Samples row(int y) {
        requireInside(0, y);
        int start = y * rowStride();
        return data.subarray(start, start + rowStride());
    }

    /** Stores the value, converted as {@link Samples#set(int, double)} converts it, into every sample. */
    public void fill(double value) {
        data.fill(value);
    }

    /**
     * Stores the value into one channel of every pixel and leaves the rest.
     *
     * @throws BadArgumentError when the layout has no such channel
     */
    public void fill(double value, int channel) {
        requireChannel(channel);
        for (int i = channel; i < data.length(); i += channels()) {
            data.set(i, value);
        }
    }

    /** The same shape with the samples copied into a new buffer. */
    public Surface copy() {
        return new Surface(data.slice(), width, height, layout(), depth());
    }

    /**
     * A black surface of the same shape, which is what every operation that cannot work in place
     * starts from.
     */
    public Surface like() {
        return create(width, height, layout(), depth());
    }

    /** A black surface of the same layout and depth with another extent. */
    public Surface like(int width, int height) {
        return create(width, height, layout(), depth());
    }

    /** A black surface of the same extent and depth with another layout. */
    public Surface like(Layout layout) {
        return create(width, height, layout, depth());
    }

    /** A black surface with every part of the shape given; what is not changing is passed as it is. */
    public Surface like(int width, int height, Layout layout, int depth) {
        return create(width, height, layout, depth);
    }

    private static int sampleCount(int width, int height, int channels) {
        long count = (long) width * height * channels;
        if (count > MAX_SAMPLES) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("width", width);
            details.put("height", height);
            details.put("channels", channels);
            details.put("samples", count);
            throw new UnsupportedError("that surface holds more samples than one buffer can", details);
        }
        return (int) count;
    }
}
