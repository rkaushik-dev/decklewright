package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adding border to a capture.
 *
 * <p>Two callers need this and they want different borders. A master that has to reach the size the
 * plan asked for wants a flat fill, because a made up edge would be a made up record. A convolution
 * that runs off the edge wants the edge repeated, because a flat fill would look like a black line
 * to it.
 */
public final class Padding {
    /** Every pad mode, in the order the lab lists them. */
    public static final List<PadMode> PAD_MODES = List.of(PadMode.values());

    private Padding() {}

    /**
     * Grows the capture by the insets with a constant black border.
     *
     * @see #pad(Surface, Insets, PadOptions)
     */
    public static Surface pad(Surface surface, Insets insets) {
        return pad(surface, insets, PadOptions.defaults());
    }

    /**
     * Grows the capture by the insets.
     *
     * @param insets whole pixels, and never negative
     * @throws BadArgumentError when an inset is negative or not a whole number, or a per channel
     *     fill has the wrong number of values
     */
    public static Surface pad(Surface surface, Insets insets, PadOptions options) {
        Objects.requireNonNull(options, "options");
        PadMode mode = options.mode();
        for (double edge : new double[] {insets.top(), insets.right(), insets.bottom(), insets.left()}) {
            if (edge < 0 || edge != Math.rint(edge)) {
                throw new BadArgumentError(
                        "padding must be whole pixels and never negative", Map.of("insets", insets.toJson()));
            }
        }

        int top = (int) insets.top();
        int left = (int) insets.left();
        int width = (int) (surface.width() + insets.horizontal());
        int height = (int) (surface.height() + insets.vertical());
        Surface out = Surface.create(width, height, surface.layout(), surface.depth());
        int[] border = Arrays.stream(options.fill().requireChannels(surface.channels()))
                .mapToInt(value -> Samples.storedValue(surface.depth(), value))
                .toArray();
        int channels = surface.channels();

        for (int y = 0; y < height; y += 1) {
            int sy = sourceIndex(y - top, surface.height(), mode);
            Samples target = out.row(y);
            Samples source = sy < 0 ? null : surface.row(sy);
            for (int x = 0; x < width; x += 1) {
                int sx = sourceIndex(x - left, surface.width(), mode);
                int to = x * channels;
                if (source == null || sx < 0) {
                    for (int c = 0; c < channels; c += 1) {
                        target.set(to + c, border[c]);
                    }
                } else {
                    int from = sx * channels;
                    for (int c = 0; c < channels; c += 1) {
                        target.set(to + c, source.get(from + c));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Grows a capture to a target size with a constant black border.
     *
     * @see #padTo(Surface, Size, PadOptions)
     */
    public static Surface padTo(Surface surface, Size size) {
        return padTo(surface, size, PadOptions.defaults());
    }

    /**
     * Grows a capture to a target size, splitting the extra border between the two sides. An odd
     * remainder goes on the trailing edge, which keeps the result stable when the same call runs
     * twice.
     *
     * @throws BadArgumentError when the target is smaller than the capture either way, or not a
     *     whole number of pixels larger
     */
    public static Surface padTo(Surface surface, Size size, PadOptions options) {
        double extraX = size.width() - surface.width();
        double extraY = size.height() - surface.height();
        if (extraX < 0 || extraY < 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("from", surface.size().toJson());
            details.put("to", size.toJson());
            throw new BadArgumentError("padTo cannot shrink a capture", details);
        }
        double left = Math.floor(extraX / 2);
        double top = Math.floor(extraY / 2);
        return pad(surface, new Insets(top, extraX - left, extraY - top, left), options);
    }

    /**
     * Pads by the same amount on every edge with a constant black border.
     *
     * @see #padUniform(Surface, double, PadOptions)
     */
    public static Surface padUniform(Surface surface, double amount) {
        return padUniform(surface, amount, PadOptions.defaults());
    }

    /**
     * Pads by the same amount on every edge, which is what a filter that needs a halo asks for.
     *
     * @throws BadArgumentError when the amount is negative or not a whole number
     */
    public static Surface padUniform(Surface surface, double amount, PadOptions options) {
        return pad(surface, Insets.uniform(amount), options);
    }

    /** The source row or column a border position copies, or minus one when it takes the fill. */
    private static int sourceIndex(int value, int extent, PadMode mode) {
        if (value >= 0 && value < extent) {
            return value;
        }
        return switch (mode) {
            case EDGE -> Num.clamp(value, 0, extent - 1);
            case MIRROR -> {
                if (extent == 1) {
                    yield 0;
                }
                long period = 2L * extent - 2;
                long wrapped = ((value % period) + period) % period;
                yield (int) (wrapped >= extent ? period - wrapped : wrapped);
            }
            case CONSTANT -> -1;
        };
    }
}
