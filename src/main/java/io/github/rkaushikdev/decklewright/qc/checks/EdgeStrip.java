package io.github.rkaushikdev.decklewright.qc.checks;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Axis;
import java.util.Objects;

/**
 * A strip of capture straddling one edge of a rect, for measuring the step across it.
 *
 * @param strip the strip, in capture pixels
 * @param across the direction the step runs in: {@link Axis#Y} for the top and bottom edges, {@link
 *     Axis#X} for the left and right
 * @see Sharpness#edgeStrips(Rect, StripOptions)
 */
public record EdgeStrip(Rect strip, Axis across) {
    /** A strip of the given parts. */
    public EdgeStrip {
        Objects.requireNonNull(strip, "strip");
        Objects.requireNonNull(across, "across");
    }
}
