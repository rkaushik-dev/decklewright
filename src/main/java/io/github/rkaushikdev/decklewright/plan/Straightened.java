package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Objects;

/**
 * Where a rect ends up once the capture is straightened, and the canvas it ends up on.
 *
 * @param rect the box round what was moved, in the straightened frame
 * @param capture the canvas of the straightened frame
 * @see CropPlanner#intoStraightFrame(Rect, Rect, io.github.rkaushikdev.decklewright.geom.Angle)
 */
public record Straightened(Rect rect, Rect capture) {

    /** A result of the given parts. */
    public Straightened {
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(capture, "capture");
    }
}
