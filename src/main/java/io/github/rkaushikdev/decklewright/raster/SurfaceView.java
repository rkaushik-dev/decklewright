package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Rounding;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A window onto part of a surface that shares its samples.
 *
 * <p>The detector runs the same measurements over a dozen candidate regions of a capture and none
 * of them need a copy. A view costs nothing to make and writes through, which is also how the
 * render stage fills one tile of an output without allocating the tile first. Coordinates are local
 * to the view, so its own top left pixel is {@code (0, 0)}.
 */
public final class SurfaceView extends Raster {
    private final Surface surface;
    private final Rect rect;
    private final int left;
    private final int top;
    private final int width;
    private final int height;

    /**
     * A view of the rect, which is given in the coordinates of the surface.
     *
     * @throws BadArgumentError when there is no surface, or the rect is not whole pixels, is empty
     *     or runs off the surface
     */
    public SurfaceView(Surface surface, Rect rect) {
        super(lookedAt(surface).layout(), surface.depth());
        Objects.requireNonNull(rect, "rect");
        if (!Rounding.isWholePixel(rect)) {
            throw new BadArgumentError("a view rect must be whole pixels", Map.of("rect", rect.toJson()));
        }
        if (rect.isEmpty()) {
            throw new BadArgumentError("a view cannot be empty", Map.of("rect", rect.toJson()));
        }
        if (!surface.bounds().containsRect(rect)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rect", rect.toJson());
            details.put("bounds", surface.bounds().toJson());
            throw new BadArgumentError("that view runs off the surface", details);
        }
        this.surface = surface;
        this.rect = rect;
        this.left = (int) rect.x();
        this.top = (int) rect.y();
        this.width = (int) rect.width();
        this.height = (int) rect.height();
    }

    /** A view of the whole surface. */
    public static SurfaceView whole(Surface surface) {
        return new SurfaceView(surface, lookedAt(surface).bounds());
    }

    /** The surface this is a window onto. */
    public Surface surface() {
        return surface;
    }

    /** The window, in the coordinates of the surface. */
    public Rect rect() {
        return rect;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    String regionName() {
        return "view";
    }

    @Override
    public int get(int x, int y, int channel) {
        requireInside(x, y);
        return surface.get(x + left, y + top, channel);
    }

    @Override
    public void set(int x, int y, int channel, int value) {
        requireInside(x, y);
        surface.set(x + left, y + top, channel, value);
    }

    @Override
    public void set(int x, int y, int channel, double value) {
        requireInside(x, y);
        surface.set(x + left, y + top, channel, value);
    }

    /** The part of one parent row this view covers, still live. */
    @Override
    public Samples row(int y) {
        requireInside(0, y);
        int start = (y + top) * surface.rowStride() + left * channels();
        return surface.data().subarray(start, start + rowStride());
    }

    /**
     * A view of part of this one, sharing the same samples.
     *
     * @param rect in the coordinates of this view
     * @throws BadArgumentError when the rect runs off this view, or is not one a view can have
     */
    public SurfaceView subview(Rect rect) {
        if (!bounds().containsRect(rect)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rect", rect.toJson());
            details.put("bounds", bounds().toJson());
            throw new BadArgumentError("that subview runs off the parent view", details);
        }
        return new SurfaceView(surface, rect.translated(left, top));
    }

    /** Copies the window out into a surface of its own, which is what anything leaving the process needs. */
    public Surface toSurface() {
        Surface out = Surface.create(width, height, layout(), depth());
        for (int y = 0; y < height; y += 1) {
            out.row(y).set(row(y));
        }
        return out;
    }

    /** The window and its surface, such as {@code view 2x2+1+0 of 4x3 gray16}. */
    @Override
    public String toString() {
        return "view " + rect + " of " + surface;
    }

    private static Surface lookedAt(Surface surface) {
        if (surface == null) {
            throw new BadArgumentError("a view needs a surface to look at", Map.of());
        }
        return surface;
    }
}
