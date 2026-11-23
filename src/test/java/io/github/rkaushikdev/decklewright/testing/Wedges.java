package io.github.rkaushikdev.decklewright.testing;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.ArrayList;
import java.util.List;

/**
 * A step wedge painted on its own, for the tests that read tone off one: a row of twenty pixel gray
 * patches, one tone each, at whatever depth is asked for. The tones are written on the eight bit
 * scale and painted at the depth's own, which is how a real wedge reads on a sixteen bit master.
 */
public final class Wedges {
    /** How wide and tall each patch is, in pixels. */
    public static final int CELL = 20;

    private Wedges() {}

    /** An eight bit wedge of the given tones. */
    public static Surface painted(double... tones) {
        return paintedAt(8, tones);
    }

    /**
     * A wedge at the given depth.
     *
     * @param tones each patch's tone on the eight bit scale, in reading order
     */
    public static Surface paintedAt(int depth, double... tones) {
        Surface surface = Surface.create(tones.length * CELL, CELL, Layout.GRAY, depth, 0);
        double scale = surface.maxValue() / 255.0;
        List<Rect> rects = cells(tones.length);
        for (int i = 0; i < tones.length; i += 1) {
            Bench.paint(surface, rects.get(i), tones[i] * scale);
        }
        return surface;
    }

    /** The cells of a wedge of so many patches, left to right. */
    public static List<Rect> cells(int count) {
        List<Rect> out = new ArrayList<>(count);
        for (int i = 0; i < count; i += 1) {
            out.add(Rect.of(i * CELL, 0, CELL, CELL));
        }
        return List.copyOf(out);
    }

    /** The wedge as the detector would hand it on: its cells, unread, which is all the checks use. */
    public static TargetFinding target(int count) {
        return targetOver(cells(count));
    }

    /** A wedge over the given cells, each a rect only. */
    public static TargetFinding targetOver(List<Rect> rects) {
        return new TargetFinding(
                Rect.empty(),
                rects.stream()
                        .map(rect -> new Patch(rect, List.of(), List.of()))
                        .toList(),
                0,
                true);
    }
}
