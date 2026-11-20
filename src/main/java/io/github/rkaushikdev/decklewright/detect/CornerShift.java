package io.github.rkaushikdev.decklewright.detect;

import java.util.List;

/**
 * How far each corner of a quad moved, in pixels.
 *
 * @param moves the distance each corner moved, top left then clockwise
 * @param worst the largest of the moves, or zero when none moved
 * @param mean the mean of the moves
 * @see Corners#cornerShift(io.github.rkaushikdev.decklewright.geom.Quad,
 *     io.github.rkaushikdev.decklewright.geom.Quad)
 */
public record CornerShift(List<Double> moves, double worst, double mean) {
    /** A shift of the given parts; the moves are copied. */
    public CornerShift {
        moves = List.copyOf(moves);
    }
}
