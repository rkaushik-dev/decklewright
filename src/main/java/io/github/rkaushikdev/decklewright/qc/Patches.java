package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.ChannelStats;
import io.github.rkaushikdev.decklewright.raster.Stats;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reading a small area of a capture, for the checks that need one.
 *
 * <p>Every check that measures a patch was doing the same three things: take a margin off so a
 * boundary that is a pixel out does not pull the reading, clip to the surface, and refuse rather
 * than return a reading off an empty view. Three copies of that is three chances for the margins to
 * drift apart, and a gray balance measured on a smaller inner area than the noise measurement is a
 * difference nobody would ever find.
 *
 * <p>The margin is a share of the patch, not a pixel count, because the wedge patches on a
 * quadrangle sheet are four times the size of the ones on a microfilm target and a fixed inset
 * would eat the small ones.
 */
public final class Patches {
    /** How much of a patch to leave off, as a share of each side. */
    public static final double PATCH_MARGIN = 0.2;

    /** How much of each grid cell to leave off, as a share of each side. */
    private static final double GRID_MARGIN = 0.25;

    /** The share of the range above which a patch is taken to be clipped. */
    private static final double FLAT_CEILING = 0.9;

    /** The share of the range below which a patch is too dark to measure variation on. */
    private static final double FLAT_FLOOR = 0.25;

    private Patches() {}

    /**
     * The inner part of a patch, leaving {@link #PATCH_MARGIN} off each side.
     *
     * @see #patchView(Surface, Rect, double)
     */
    public static Optional<SurfaceView> patchView(Surface surface, Rect rect) {
        return patchView(surface, rect, PATCH_MARGIN);
    }

    /**
     * The inner part of a patch, which is the only part worth measuring: the given share of its
     * width and of its height, rounded down to whole pixels, left off each side, and what remains
     * clipped to the capture.
     *
     * @return empty when there is nothing left to read
     * @throws io.github.rkaushikdev.decklewright.BadArgumentError when what remains is not whole
     *     pixels
     */
    public static Optional<SurfaceView> patchView(Surface surface, Rect rect, double margin) {
        double insetX = Math.floor(rect.width() * margin);
        double insetY = Math.floor(rect.height() * margin);
        Rect inner = rect.inset(insetX, insetY).intersect(surface.bounds());
        if (inner.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SurfaceView(surface, inner));
    }

    /**
     * The mean of every channel over the inner part of a patch.
     *
     * @see #channelMeans(Surface, Rect, double)
     */
    public static double[] channelMeans(Surface surface, Rect rect) {
        return channelMeans(surface, rect, PATCH_MARGIN);
    }

    /**
     * The mean of every channel over a patch, with the given share left off each side.
     *
     * @return one mean per channel, in channel order; empty when the patch could not be read
     */
    public static double[] channelMeans(Surface surface, Rect rect, double margin) {
        return patchView(surface, rect, margin)
                .map(view -> Stats.allChannelStats(view).stream()
                        .mapToDouble(ChannelStats::mean)
                        .toArray())
                .orElseGet(() -> new double[0]);
    }

    /**
     * The mean of the first channel of each patch, read whole.
     *
     * @see #patchMeans(Surface, List, double)
     */
    public static double[] patchMeans(Surface surface, List<Rect> rects) {
        return patchMeans(surface, rects, 0);
    }

    /**
     * The mean of the first channel of each of a list of patches, dropping the ones that fell off
     * the capture.
     *
     * @param margin the share of each patch to leave off each side
     * @return one mean per patch that could be read, in the order given
     */
    public static double[] patchMeans(Surface surface, List<Rect> rects, double margin) {
        List<Double> out = new ArrayList<>(rects.size());
        for (Rect rect : rects) {
            patchView(surface, rect, margin)
                    .ifPresent(view -> out.add(Stats.channelStats(view, 0).mean()));
        }
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * A grid of patches over a region, a quarter of each cell left off each side.
     *
     * @see #patchGrid(Rect, int, int, double)
     */
    public static List<Rect> patchGrid(Rect within, int across, int down) {
        return patchGrid(within, across, down, GRID_MARGIN);
    }

    /**
     * A grid of patches over a region, each one a fraction of the cell so a patch that lands on an
     * edge does not take the edge with it. The edges are rounded to whole pixels, half up, and a
     * patch is never less than a pixel either way.
     *
     * @param across how many cells across
     * @param down how many cells down
     * @param margin the share of each cell to leave off each side
     * @return the patches, row by row from the top left
     */
    public static List<Rect> patchGrid(Rect within, int across, int down, double margin) {
        List<Rect> out = new ArrayList<>();
        double cellWidth = within.width() / across;
        double cellHeight = within.height() / down;
        double insetX = cellWidth * margin;
        double insetY = cellHeight * margin;

        for (int row = 0; row < down; row += 1) {
            for (int column = 0; column < across; column += 1) {
                double left = Num.roundHalfUp(within.left() + column * cellWidth + insetX);
                double top = Num.roundHalfUp(within.top() + row * cellHeight + insetY);
                double right = Num.roundHalfUp(within.left() + (column + 1) * cellWidth - insetX);
                double bottom = Num.roundHalfUp(within.top() + (row + 1) * cellHeight - insetY);
                out.add(Rect.fromEdges(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom)));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The plain grid, with nothing kept off it.
     *
     * @see #gridOver(Rect, int, int, double, List)
     */
    public static List<Rect> gridOver(Rect within, int across, int down) {
        return gridOver(within, across, down, GRID_MARGIN, List.of());
    }

    /**
     * The grid at the usual margin, with everything named kept off it.
     *
     * @see #gridOver(Rect, int, int, double, List)
     */
    public static List<Rect> gridOver(Rect within, int across, int down, List<Rect> avoid) {
        return gridOver(within, across, down, GRID_MARGIN, avoid);
    }

    /**
     * The same grid with everything the caller named kept off it. A patch that so much as touches
     * one of them is dropped whole rather than trimmed, because a trimmed patch is a smaller sample
     * of a different place and comparing it against the others is what the caller was trying to
     * avoid.
     *
     * @param margin the share of each cell to leave off each side
     * @param avoid what no patch may overlap
     */
    public static List<Rect> gridOver(Rect within, int across, int down, double margin, List<Rect> avoid) {
        List<Rect> grid = patchGrid(within, across, down, margin);
        if (avoid.isEmpty()) {
            return grid;
        }
        return grid.stream()
                .filter(patch -> avoid.stream().noneMatch(patch::intersects))
                .toList();
    }

    /**
     * The lightest patch between a quarter and nine tenths of the range.
     *
     * @see #lightestFlatPatch(Surface, List, double, double)
     */
    public static Optional<SurfaceView> lightestFlatPatch(Surface surface, List<Patch> patches) {
        return lightestFlatPatch(surface, patches, FLAT_CEILING, FLAT_FLOOR);
    }

    /**
     * The patch a measurement of variation should use: light enough that the reading is
     * representative and not so light that it is clipped. Each patch is read over its inner part,
     * on the first channel.
     *
     * @param ceiling share of the range above which a patch is clipped
     * @param floor share of the range below which a patch is too dark
     * @return the inner part of the lightest patch inside the window, the first of them on a tie;
     *     empty when none is
     */
    public static Optional<SurfaceView> lightestFlatPatch(
            Surface surface, List<Patch> patches, double ceiling, double floor) {
        double top = surface.maxValue();
        SurfaceView best = null;
        double bestMean = 0;
        for (Patch patch : patches) {
            Optional<SurfaceView> view = patchView(surface, patch.rect());
            if (view.isEmpty()) {
                continue;
            }
            double mean = Stats.channelStats(view.get(), 0).mean();
            if (mean > top * ceiling || mean < top * floor) {
                continue;
            }
            if (best == null || mean > bestMean) {
                best = view.get();
                bestMean = mean;
            }
        }
        return Optional.ofNullable(best);
    }
}
