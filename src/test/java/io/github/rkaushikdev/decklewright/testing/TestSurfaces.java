package io.github.rkaushikdev.decklewright.testing;

import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Raster;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.ArrayList;
import java.util.List;

/**
 * Small hand made surfaces, the kind a test can check sample by sample: a counting ramp, a gray
 * surface written out row by row, and the rows of a region read back as plain arrays.
 */
public final class TestSurfaces {
    private TestSurfaces() {}

    /** An eight bit gray surface whose samples count up from zero, row by row. */
    public static Surface ramp(int width, int height) {
        Surface s = Surface.create(width, height, Layout.GRAY);
        for (int y = 0; y < height; y += 1) {
            for (int x = 0; x < width; x += 1) {
                s.set(x, y, 0, y * width + x);
            }
        }
        return s;
    }

    /** An eight bit gray surface holding exactly these rows, which must all be the same length. */
    public static Surface gray(int[]... rows) {
        return gray(8, rows);
    }

    /** A gray surface of the depth holding exactly these rows, which must all be the same length. */
    public static Surface gray(int depth, int[]... rows) {
        Surface s = Surface.create(rows[0].length, rows.length, Layout.GRAY, depth);
        for (int y = 0; y < rows.length; y += 1) {
            for (int x = 0; x < rows[y].length; x += 1) {
                s.set(x, y, 0, rows[y][x]);
            }
        }
        return s;
    }

    /** Every row of the region, top first, as its samples. */
    public static List<int[]> rowsOf(Raster region) {
        List<int[]> out = new ArrayList<>(region.height());
        for (Samples row : region.rows()) {
            out.add(row.toArray());
        }
        return out;
    }

    /** One row, written as a list of samples for readability at the call site. */
    public static int[] row(int... samples) {
        return samples;
    }
}
