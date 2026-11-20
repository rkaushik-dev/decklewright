package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Along;
import io.github.rkaushikdev.decklewright.raster.Stats;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The neatline: the ruled border a map sheet is drawn inside.
 *
 * <p>On a quadrangle the neatline is the actual map. Everything outside it is collar, which carries
 * the legend, the scale bar, the credits and the grid ticks. Two of the collections crop to the
 * neatline and two crop to the paper edge, and the difference is about an inch and a half of
 * content, so nobody gets to guess.
 *
 * <p>It is found as the strongest dark ruled line within a band inside each paper edge. A ruled
 * line is dark across its whole length, so it shows up in the mean profile as a narrow trough far
 * below the paper around it.
 */
public final class Neatlines {
    /** The share of the sheet searched in from each edge unless the caller says otherwise. */
    static final double DEFAULT_BAND = 0.35;

    /** The contrast, in sample values, a ruled line must reach unless the caller says otherwise. */
    static final double DEFAULT_MINIMUM_CONTRAST = 12;

    private Neatlines() {}

    /**
     * How dark each row or each column of a region is: full scale less its mean, in the first
     * channel.
     *
     * @param within the region, in capture pixels
     * @param along one value per row or one per column
     * @throws BadArgumentError when the region is not whole pixels, is empty or runs off the capture
     */
    public static double[] darkness(Surface surface, Rect within, Along along) {
        double[] profile = Stats.meanProfile(new SurfaceView(surface, within), along);
        double[] out = new double[profile.length];
        int top = surface.maxValue();
        for (int i = 0; i < profile.length; i += 1) {
            out[i] = top - profile[i];
        }
        return out;
    }

    /**
     * The strongest trough in a run, taken as the largest rise above the run's own median.
     * Returning the contrast alongside the index lets the caller decide whether it found a ruled
     * line or just paper grain.
     *
     * @param from the index to start at
     * @param to the index to stop before; the two may be given either way round, and are held to the
     *     profile
     * @return the trough, or nothing at index minus one when the run is shorter than two
     */
    public static Trough strongestLine(double[] profile, int from, int to) {
        int start = Math.max(0, Math.min(from, to));
        int end = Math.min(profile.length, Math.max(from, to));
        if (end - start < 2) {
            return new Trough(-1, 0);
        }

        double[] sorted = Arrays.copyOfRange(profile, start, end);
        Arrays.sort(sorted);
        double middle = sorted[sorted.length / 2];

        int at = -1;
        double best = 0;
        for (int i = start; i < end; i += 1) {
            double contrast = profile[i] - middle;
            if (contrast > best) {
                best = contrast;
                at = i;
            }
        }
        return new Trough(at, best);
    }

    /**
     * Finds the neatline searching a band of thirty five per cent in from each edge, and wanting a
     * contrast of twelve.
     *
     * @see #findNeatline(Surface, Rect, double, double)
     */
    public static Neatline findNeatline(Surface surface, Rect sheet) {
        return findNeatline(surface, sheet, DEFAULT_BAND, DEFAULT_MINIMUM_CONTRAST);
    }

    /**
     * Finds the neatline as the strongest ruled line in a band inside each edge of the sheet. A side
     * with nothing convincing falls back to the paper edge and is named as missing.
     *
     * @param sheet the paper edge, already square to the axes, in whole capture pixels
     * @param band the share of the sheet searched in from each edge; it has to reach past the legend
     *     collar at the bottom of a quadrangle
     * @param minimumContrast how strong a ruled line must be, in sample values
     * @throws BadArgumentError when the band is outside 0.01 to 0.49, or the sheet is not whole pixels
     *     on the capture
     */
    public static Neatline findNeatline(Surface surface, Rect sheet, double band, double minimumContrast) {
        Guards.requireInRange(band, 0.01, 0.49, "band");

        double[] rows = darkness(surface, sheet, Along.ROW);
        double[] columns = darkness(surface, sheet, Along.COLUMN);
        int bandY = (int) Math.max(2, Math.round(sheet.height() * band));
        int bandX = (int) Math.max(2, Math.round(sheet.width() * band));

        Trough top = strongestLine(rows, 0, bandY);
        Trough bottom = strongestLine(rows, rows.length - bandY, rows.length);
        Trough left = strongestLine(columns, 0, bandX);
        Trough right = strongestLine(columns, columns.length - bandX, columns.length);

        boolean foundTop = top.contrast() >= minimumContrast;
        boolean foundRight = right.contrast() >= minimumContrast;
        boolean foundBottom = bottom.contrast() >= minimumContrast;
        boolean foundLeft = left.contrast() >= minimumContrast;

        double edgeTop = foundTop ? sheet.top() + top.at() : sheet.top();
        double edgeBottom = foundBottom ? sheet.top() + bottom.at() + 1 : sheet.bottom();
        double edgeLeft = foundLeft ? sheet.left() + left.at() : sheet.left();
        double edgeRight = foundRight ? sheet.left() + right.at() + 1 : sheet.right();

        List<Side> missing = new ArrayList<>();
        if (!foundTop) {
            missing.add(Side.TOP);
        }
        if (!foundRight) {
            missing.add(Side.RIGHT);
        }
        if (!foundBottom) {
            missing.add(Side.BOTTOM);
        }
        if (!foundLeft) {
            missing.add(Side.LEFT);
        }

        Map<Side, Double> contrast = new EnumMap<>(Side.class);
        contrast.put(Side.TOP, top.contrast());
        contrast.put(Side.RIGHT, right.contrast());
        contrast.put(Side.BOTTOM, bottom.contrast());
        contrast.put(Side.LEFT, left.contrast());

        Rect rect = Rect.fromEdges(edgeLeft, edgeTop, Math.max(edgeLeft, edgeRight), Math.max(edgeTop, edgeBottom));
        return new Neatline(rect, contrast, missing);
    }

    /**
     * How much paper sits between the neatline and the sheet edge, per side. This is the collar, and
     * the profiles quote it in millimetres.
     *
     * @return the collar on each side, in pixels
     */
    public static Insets collarWidths(Rect sheet, Rect neatline) {
        return new Insets(
                neatline.top() - sheet.top(),
                sheet.right() - neatline.right(),
                sheet.bottom() - neatline.bottom(),
                neatline.left() - sheet.left());
    }

    /**
     * A quadrangle has a wide collar at the bottom for the legend and narrower ones elsewhere. A
     * neatline that came back with the widest collar somewhere else has probably locked onto a grid
     * line rather than the border. A tie goes to the side met first, top then clockwise.
     */
    public static boolean looksLikeQuadrangle(Insets widths) {
        Side widest = Side.TOP;
        for (Side side : Edges.SIDES) {
            if (widthOn(widths, side) > widthOn(widths, widest)) {
                widest = side;
            }
        }
        return widest == Side.BOTTOM && widths.bottom() > 0;
    }

    private static double widthOn(Insets widths, Side side) {
        return switch (side) {
            case TOP -> widths.top();
            case RIGHT -> widths.right();
            case BOTTOM -> widths.bottom();
            case LEFT -> widths.left();
        };
    }
}
