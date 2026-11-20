package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.geom.Rounding;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cutting a rectangle out of a capture.
 *
 * <p>A planned crop is real valued and often runs a little past the edge of the bed, because bleed
 * is measured outward from the sheet and the sheet was placed near the edge. Three answers are
 * possible and the caller has to say which one it wants rather than getting a silent clamp. A rect
 * that is not whole pixels is rounded first, {@linkplain RoundPolicy#OUTWARD outward} unless the
 * caller says otherwise.
 */
public final class Crops {
    private Crops() {}

    /**
     * Cuts out a rect that must already lie on the capture, rounded outward.
     *
     * @see #crop(Surface, Rect, RoundPolicy)
     */
    public static Surface crop(Surface surface, Rect rect) {
        return crop(surface, rect, RoundPolicy.OUTWARD);
    }

    /**
     * Cuts out a rect that must already lie on the capture, into a surface of its own.
     *
     * @throws BadArgumentError when the rounded rect has no area or runs off the capture
     */
    public static Surface crop(Surface surface, Rect rect, RoundPolicy round) {
        Rect pixels = toPixelRect(rect, round);
        if (pixels.isEmpty()) {
            throw new BadArgumentError("that crop has no area", Map.of("rect", rect.toJson()));
        }
        if (!surface.bounds().containsRect(pixels)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rect", pixels.toJson());
            details.put("bounds", surface.bounds().toJson());
            throw new BadArgumentError("that crop runs off the capture", details);
        }
        return new SurfaceView(surface, pixels).toSurface();
    }

    /**
     * The same cut as {@link #crop(Surface, Rect)} with no copy, for measurement rather than output.
     *
     * @see #cropView(Surface, Rect, RoundPolicy)
     */
    public static SurfaceView cropView(Surface surface, Rect rect) {
        return cropView(surface, rect, RoundPolicy.OUTWARD);
    }

    /**
     * A live view of the rounded rect.
     *
     * @throws BadArgumentError when the rounded rect is empty or runs off the capture
     */
    public static SurfaceView cropView(Surface surface, Rect rect, RoundPolicy round) {
        return new SurfaceView(surface, toPixelRect(rect, round));
    }

    /**
     * Cuts what overlaps the capture and drops the rest, rounded outward.
     *
     * @see #cropClamped(Surface, Rect, RoundPolicy)
     */
    public static Surface cropClamped(Surface surface, Rect rect) {
        return cropClamped(surface, rect, RoundPolicy.OUTWARD);
    }

    /**
     * Cuts what overlaps the capture and drops the rest, which is what a preview wants.
     *
     * @throws BadArgumentError when the rounded rect does not touch the capture at all
     */
    public static Surface cropClamped(Surface surface, Rect rect, RoundPolicy round) {
        Rect pixels = toPixelRect(rect, round).intersect(surface.bounds());
        if (pixels.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rect", rect.toJson());
            details.put("bounds", surface.bounds().toJson());
            throw new BadArgumentError("that crop does not touch the capture", details);
        }
        return new SurfaceView(surface, pixels).toSurface();
    }

    /**
     * Cuts the rect as asked, rounded outward, with black where it falls off the capture.
     *
     * @see #cropPadded(Surface, Rect, Fill, RoundPolicy)
     */
    public static PaddedCrop cropPadded(Surface surface, Rect rect) {
        return cropPadded(surface, rect, Fill.uniform(0), RoundPolicy.OUTWARD);
    }

    /**
     * Cuts the rect as asked, rounded outward, filling where it falls off the capture.
     *
     * @see #cropPadded(Surface, Rect, Fill, RoundPolicy)
     */
    public static PaddedCrop cropPadded(Surface surface, Rect rect, Fill fill) {
        return cropPadded(surface, rect, fill, RoundPolicy.OUTWARD);
    }

    /**
     * Cuts the rect as asked and fills whatever fell outside the capture. This is the one a master
     * uses, because the output has to be the size the plan said even when the sheet was placed too
     * near the edge of the bed.
     *
     * @param fill one value per channel, or one for all of them
     * @throws BadArgumentError when the rounded rect has no area, or a per channel fill has the
     *     wrong number of values
     */
    public static PaddedCrop cropPadded(Surface surface, Rect rect, Fill fill, RoundPolicy round) {
        Rect pixels = toPixelRect(rect, round);
        if (pixels.isEmpty()) {
            throw new BadArgumentError("that crop has no area", Map.of("rect", rect.toJson()));
        }
        Surface out = Surface.create((int) pixels.width(), (int) pixels.height(), surface.layout(), surface.depth());
        double[] values = Objects.requireNonNull(fill, "fill").requireChannels(surface.channels());
        if (hasAnyValue(values)) {
            fillEveryPixel(out, values);
        }

        Rect overlap = pixels.intersect(surface.bounds());
        if (overlap.isEmpty()) {
            return new PaddedCrop(out, true, Rect.empty());
        }
        SurfaceView source = new SurfaceView(surface, overlap);
        SurfaceView target = new SurfaceView(out, overlap.translated(-pixels.x(), -pixels.y()));
        for (int y = 0; y < source.height(); y += 1) {
            target.row(y).set(source.row(y));
        }
        return new PaddedCrop(out, overlap.area() < pixels.area(), overlap);
    }

    private static Rect toPixelRect(Rect rect, RoundPolicy policy) {
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(policy, "round");
        return Rounding.isWholePixel(rect) ? rect : Rounding.roundRect(rect, policy);
    }

    private static boolean hasAnyValue(double[] values) {
        for (double value : values) {
            if (value != 0) {
                return true;
            }
        }
        return false;
    }

    /** Every pixel set to the fill, which is converted to the depth once rather than per sample. */
    private static void fillEveryPixel(Surface out, double[] values) {
        int[] stored = Arrays.stream(values)
                .mapToInt(value -> Samples.storedValue(out.depth(), value))
                .toArray();
        Samples data = out.data();
        for (int i = 0; i < data.length(); i += 1) {
            data.set(i, stored[i % stored.length]);
        }
    }
}
