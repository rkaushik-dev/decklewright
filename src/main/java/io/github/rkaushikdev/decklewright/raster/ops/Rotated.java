package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Matrix;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Objects;

/**
 * What a {@linkplain Rotation#rotate rotation} produced and how it got there.
 *
 * @param surface the rotated capture; the very surface handed in when there was nothing to do
 * @param transform where the resample put each sample of the quarter turned capture, from its
 *     coordinates into the output's; the identity when no resample ran
 * @param residual the part of the angle left after the quarter turns, which the resample took out
 * @param quarterTurns the clockwise quarter turns taken out losslessly first, zero through three
 */
public record Rotated(Surface surface, Matrix transform, Angle residual, int quarterTurns) {
    /** A rotation of the given parts. */
    public Rotated {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(residual, "residual");
    }
}
