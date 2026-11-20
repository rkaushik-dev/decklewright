package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Depth and layout conversion.
 *
 * <p>Eight to sixteen bits multiplies by two hundred and fifty seven rather than shifting left by
 * eight, so full scale stays full scale. Shifting leaves white at sixty five two hundred and eighty,
 * which a quality check then reports as a capture that never reaches white.
 *
 * <p>Every conversion returns a new surface, including one that had nothing to change.
 */
public final class Conversions {
    /** Rec 709 luma weights, which is what the derivative recipes assume. */
    public static final List<Double> LUMA_709 = List.of(0.2126, 0.7152, 0.0722);

    /** Rec 601 luma, kept because the older service copies were built with it. */
    public static final List<Double> LUMA_601 = List.of(0.299, 0.587, 0.114);

    private Conversions() {}

    /**
     * One sample moved from one depth to another: times two hundred and fifty seven going up, and
     * divided by it and rounded half up going down.
     */
    public static int scaleSample(int value, int from, int to) {
        if (from == to) {
            return value;
        }
        if (from == 8) {
            return value * 257;
        }
        return (int) Num.roundHalfUp(value / 257.0);
    }

    /**
     * The surface at another depth.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     */
    public static Surface toDepth(Surface surface, int depth) {
        Layouts.requireDepth(depth);
        if (surface.depth() == depth) {
            return surface.copy();
        }
        Surface out = surface.like(surface.width(), surface.height(), surface.layout(), depth);
        int from = surface.depth();
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            Samples target = out.row(y);
            for (int i = 0; i < source.length(); i += 1) {
                target.set(i, scaleSample(source.get(i), from, depth));
            }
        }
        return out;
    }

    /**
     * Gray by the {@linkplain #LUMA_709 Rec 709} weights.
     *
     * @see #toGray(Surface, List)
     */
    public static Surface toGray(Surface surface) {
        return toGray(surface, LUMA_709);
    }

    /**
     * Gray by the given luma weights, keeping alpha when there is one. A surface that is already
     * gray is copied as it is, whatever the weights.
     *
     * @param weights red, green and blue, in that order
     * @throws BadArgumentError when there are not exactly three weights
     */
    public static Surface toGray(Surface surface, List<Double> weights) {
        if (!surface.layout().isColour()) {
            return surface.copy();
        }
        if (weights.size() != 3) {
            throw new BadArgumentError("luma needs exactly three weights", Map.of("given", weights.size()));
        }
        double red = weights.get(0);
        double green = weights.get(1);
        double blue = weights.get(2);
        boolean keepAlpha = surface.layout().hasAlpha();
        Surface out = surface.like(keepAlpha ? Layout.GRAYA : Layout.GRAY);
        int alpha = surface.layout().alphaIndex();
        int top = surface.maxValue();
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            Samples target = out.row(y);
            for (int x = 0; x < surface.width(); x += 1) {
                int base = x * surface.channels();
                double luma = source.get(base) * red + source.get(base + 1) * green + source.get(base + 2) * blue;
                int to = x * out.channels();
                target.set(to, (int) Num.clamp(Num.roundHalfUp(luma), 0, top));
                if (keepAlpha) {
                    target.set(to + 1, source.get(base + alpha));
                }
            }
        }
        return out;
    }

    /** Colour by copying the gray value into all three channels, keeping alpha when there is one. */
    public static Surface toColour(Surface surface) {
        if (surface.layout().isColour()) {
            return surface.copy();
        }
        boolean keepAlpha = surface.layout().hasAlpha();
        Surface out = surface.like(keepAlpha ? Layout.RGBA : Layout.RGB);
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            Samples target = out.row(y);
            for (int x = 0; x < surface.width(); x += 1) {
                int value = source.get(x * surface.channels());
                int to = x * out.channels();
                target.set(to, value);
                target.set(to + 1, value);
                target.set(to + 2, value);
                if (keepAlpha) {
                    target.set(to + 3, source.get(x * surface.channels() + 1));
                }
            }
        }
        return out;
    }

    /**
     * The same colour with a fully opaque coverage channel added.
     *
     * @see #addAlpha(Surface, double)
     */
    public static Surface addAlpha(Surface surface) {
        return addAlpha(surface, surface.maxValue());
    }

    /**
     * The same colour with a coverage channel added. A surface that already has one is copied as it
     * is.
     *
     * @param opaque the value every sample's coverage gets, stored as {@link Samples#set(int,
     *     double)} stores it
     */
    public static Surface addAlpha(Surface surface, double opaque) {
        if (surface.layout().hasAlpha()) {
            return surface.copy();
        }
        Surface out = surface.like(surface.layout().withAlpha());
        int colours = surface.layout().colourCount();
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            Samples target = out.row(y);
            for (int x = 0; x < surface.width(); x += 1) {
                int from = x * surface.channels();
                int to = x * out.channels();
                for (int c = 0; c < colours; c += 1) {
                    target.set(to + c, source.get(from + c));
                }
                target.set(to + colours, opaque);
            }
        }
        return out;
    }

    /**
     * Composites over black and drops the coverage channel.
     *
     * @see #dropAlpha(Surface, Fill)
     */
    public static Surface dropAlpha(Surface surface) {
        return dropAlpha(surface, Fill.uniform(0));
    }

    /**
     * Composites over a flat background and drops the coverage channel. A master that was rotated
     * has transparent corners and the archive will not accept a file with an alpha channel, so this
     * is where those corners become paper white or bed black depending on the profile. A surface
     * with no coverage channel is copied as it is.
     *
     * @param background one value per colour channel, or one for all of them
     * @throws BadArgumentError when a per channel background has the wrong number of values
     */
    public static Surface dropAlpha(Surface surface, Fill background) {
        if (!surface.layout().hasAlpha()) {
            return surface.copy();
        }
        Surface out = surface.like(surface.layout().withoutAlpha());
        int colours = surface.layout().colourCount();
        double[] values = Objects.requireNonNull(background, "background").forChannels(colours);
        if (values.length != colours) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("given", values.length);
            details.put("expected", colours);
            throw new BadArgumentError("the background has the wrong number of channels", details);
        }
        int top = surface.maxValue();
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            Samples target = out.row(y);
            for (int x = 0; x < surface.width(); x += 1) {
                int from = x * surface.channels();
                int to = x * out.channels();
                double coverage = (double) source.get(from + colours) / top;
                for (int c = 0; c < colours; c += 1) {
                    double mixed = source.get(from + c) * coverage + values[c] * (1 - coverage);
                    target.set(to + c, (int) Num.clamp(Num.roundHalfUp(mixed), 0, top));
                }
            }
        }
        return out;
    }

    /**
     * The surface in another layout, composited over black if coverage has to go.
     *
     * @see #toLayout(Surface, Layout, Fill)
     */
    public static Surface toLayout(Surface surface, Layout layout) {
        return toLayout(surface, layout, Fill.uniform(0));
    }

    /**
     * The surface in another layout, by way of colour or gray first and coverage second.
     *
     * @param background what coverage is composited over if it has to go
     * @throws BadArgumentError when a per channel background has the wrong number of values
     * @throws UnsupportedError when no conversion reaches the layout
     */
    public static Surface toLayout(Surface surface, Layout layout, Fill background) {
        Objects.requireNonNull(layout, "layout");
        if (surface.layout() == layout) {
            return surface.copy();
        }

        Surface current = surface;
        if (layout.isColour() && !current.layout().isColour()) {
            current = toColour(current);
        }
        if (!layout.isColour() && current.layout().isColour()) {
            current = toGray(current);
        }
        if (layout.hasAlpha() && !current.layout().hasAlpha()) {
            current = addAlpha(current);
        }
        if (!layout.hasAlpha() && current.layout().hasAlpha()) {
            current = dropAlpha(current, background);
        }

        if (current.layout() != layout) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("from", surface.layout().label());
            details.put("to", layout.label());
            throw new UnsupportedError("no route from that layout to this one", details);
        }
        return current == surface ? surface.copy() : current;
    }

    /**
     * How many bytes a surface of this shape would take, which the pipeline uses to decide whether a
     * job fits alongside the others already running.
     *
     * @throws BadArgumentError when the depth is not one the lab works in
     */
    public static long byteSize(int width, int height, Layout layout, int depth) {
        Objects.requireNonNull(layout, "layout");
        return (long) width * height * layout.channelCount() * (Layouts.requireDepth(depth) / 8);
    }
}
