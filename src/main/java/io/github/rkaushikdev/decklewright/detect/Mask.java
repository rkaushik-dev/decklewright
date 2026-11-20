package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.raster.Raster;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Uint8Samples;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A binary mask over a capture.
 *
 * <p>There used to be a second way in here, a threshold picked by otsu off the capture's own
 * histogram. It was the first pass at finding a sheet and it only worked when the bed and the paper
 * were the two things in the frame. The moment the colour target went on the bed it started
 * splitting the target off instead. The background estimate replaced it and this is now the plain
 * threshold and nothing else.
 *
 * <p>One byte per pixel rather than one bit. The memory is not the constraint on this hardware and
 * the bit twiddling version was measurably slower on the morphology passes, which are what actually
 * cost time.
 *
 * <p>Set means object. The bed is zero. The extent is fixed; the pixels are not, and {@link #row(int)}
 * and {@link #data()} are live windows onto them.
 */
public final class Mask {
    private final int width;
    private final int height;

    /** One byte per pixel, row by row. Package-private so the detectors' inner loops read it directly. */
    final byte[] data;

    /**
     * An empty mask of the given extent.
     *
     * @throws BadArgumentError when either extent is not a whole number above zero
     */
    public Mask(int width, int height) {
        this.width = Guards.requirePositiveInteger(width, "width");
        this.height = Guards.requirePositiveInteger(height, "height");
        this.data = new byte[Math.multiplyExact(width, height)];
    }

    private Mask(int width, int height, byte[] data) {
        this.width = Guards.requirePositiveInteger(width, "width");
        this.height = Guards.requirePositiveInteger(height, "height");
        int expected = Math.multiplyExact(width, height);
        if (data.length != expected) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("given", data.length);
            details.put("expected", expected);
            throw new BadArgumentError("the mask buffer does not match the stated geometry", details);
        }
        this.data = data;
    }

    /**
     * A mask over an existing buffer, one byte per pixel row by row, zero for bed and one for
     * object. The buffer is not copied: writing either one writes the other.
     *
     * @throws BadArgumentError when either extent is not a whole number above zero, or the buffer is
     *     not exactly one byte per pixel
     */
    public static Mask wrap(int width, int height, byte[] data) {
        return new Mask(width, height, Objects.requireNonNull(data, "data"));
    }

    /**
     * Set where the first channel is above the threshold.
     *
     * @see #threshold(Raster, double, int, boolean)
     */
    public static Mask threshold(Raster source, double threshold) {
        return threshold(source, threshold, 0, true);
    }

    /**
     * Set where one channel of the source is above the threshold, or below it. A sample equal to
     * the threshold is not set either way. A view is read in its own local coordinates.
     *
     * @param channel the channel to read
     * @param above set where the sample is above the threshold; below it when false
     * @throws BadArgumentError when the layout has no such channel
     */
    public static Mask threshold(Raster source, double threshold, int channel, boolean above) {
        int channels = source.channels();
        if (channel < 0 || channel >= channels) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("channel", channel);
            details.put("channels", channels);
            throw new BadArgumentError("that channel is outside the layout", details);
        }
        Mask mask = new Mask(source.width(), source.height());
        int at = 0;
        for (int y = 0; y < source.height(); y += 1) {
            Samples row = source.row(y);
            for (int x = 0; x < source.width(); x += 1) {
                int value = row.get(x * channels + channel);
                mask.data[at] = (byte) ((above ? value > threshold : value < threshold) ? 1 : 0);
                at += 1;
            }
        }
        return mask;
    }

    /** Pixels across. */
    public int width() {
        return width;
    }

    /** Pixels down. */
    public int height() {
        return height;
    }

    /** The extent. */
    public Size size() {
        return new Size(width, height);
    }

    /** The bounds, always at the origin. */
    public Rect bounds() {
        return Rect.of(0, 0, width, height);
    }

    /** The pixel's byte, which is one or zero for any mask this layer made, and zero outside the mask. */
    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return data[y * width + x] & 0xff;
    }

    /** Sets the pixel for any value but zero and clears it for zero. A write outside the mask is ignored. */
    public void set(int x, int y, int value) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        data[y * width + x] = (byte) (value != 0 ? 1 : 0);
    }

    /**
     * A live window onto one row; writing it writes the mask.
     *
     * @throws IndexOutOfBoundsException when the row is outside the mask
     */
    public Uint8Samples row(int y) {
        Objects.checkIndex(y, height);
        return Uint8Samples.wrap(data, y * width, width);
    }

    /** A live window onto every pixel, row by row; writing it writes the mask. */
    public Uint8Samples data() {
        return Uint8Samples.wrap(data);
    }

    /** How many pixels are set: the sum of the bytes, which is the count for any mask this layer made. */
    public long count() {
        long total = 0;
        for (byte value : data) {
            total += value & 0xff;
        }
        return total;
    }

    /** The share of the capture that is set. */
    public double coverage() {
        return (double) count() / data.length;
    }

    /** A copy with a buffer of its own (the original's {@code clone()}). */
    public Mask copy() {
        return new Mask(width, height, data.clone());
    }

    /** Set exactly where this is not. */
    public Mask inverted() {
        Mask out = new Mask(width, height);
        for (int i = 0; i < data.length; i += 1) {
            out.data[i] = (byte) (data[i] != 0 ? 0 : 1);
        }
        return out;
    }

    /**
     * Set where both are.
     *
     * @throws BadArgumentError when the two are not the same shape
     */
    public Mask and(Mask other) {
        requireSameShape(other);
        Mask out = new Mask(width, height);
        for (int i = 0; i < data.length; i += 1) {
            out.data[i] = (byte) (data[i] & other.data[i]);
        }
        return out;
    }

    /**
     * Set where either is.
     *
     * @throws BadArgumentError when the two are not the same shape
     */
    public Mask or(Mask other) {
        requireSameShape(other);
        Mask out = new Mask(width, height);
        for (int i = 0; i < data.length; i += 1) {
            out.data[i] = (byte) (data[i] | other.data[i]);
        }
        return out;
    }

    /**
     * Set where this is and the other is not.
     *
     * @throws BadArgumentError when the two are not the same shape
     */
    public Mask without(Mask other) {
        requireSameShape(other);
        Mask out = new Mask(width, height);
        for (int i = 0; i < data.length; i += 1) {
            out.data[i] = (byte) (data[i] != 0 && other.data[i] == 0 ? 1 : 0);
        }
        return out;
    }

    /**
     * The smallest rect holding every set pixel, or an empty rect when nothing is set. This is the
     * first cut at where the sheet is.
     */
    public Rect extent() {
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y += 1) {
            int start = y * width;
            for (int x = 0; x < width; x += 1) {
                if (data[start + x] == 0) {
                    continue;
                }
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < 0) {
            return Rect.empty();
        }
        return Rect.fromEdges(left, top, right + 1, bottom + 1);
    }

    /**
     * Checks the other mask has this one's extent.
     *
     * @throws BadArgumentError naming both shapes when it does not
     */
    void requireSameShape(Mask other) {
        if (other.width != width || other.height != height) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("left", shape(width, height));
            details.put("right", shape(other.width, other.height));
            throw new BadArgumentError("those two masks are not the same shape", details);
        }
    }

    /** The shape and how much is set, such as {@code mask 4x2, 1 set}. */
    @Override
    public String toString() {
        return "mask " + width + "x" + height + ", " + count() + " set";
    }

    private static Map<String, Object> shape(int width, int height) {
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("width", width);
        shape.put("height", height);
        return shape;
    }
}
