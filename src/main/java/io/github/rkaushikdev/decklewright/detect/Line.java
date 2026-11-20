package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Point;
import java.util.List;
import java.util.Objects;

/**
 * A straight line fitted to the points scanned off one side of a sheet.
 *
 * @param through the centroid of the points, which the line passes through
 * @param angle the line's direction, from minus ninety up to ninety degrees
 * @param residual the root mean square distance of the points from the line, in pixels
 * @param points the points the line was fitted to; copied
 * @see Edges#fitLine(List)
 */
public record Line(Point through, Angle angle, double residual, List<Point> points) {
    /** A line of the given parts. */
    public Line {
        Objects.requireNonNull(through, "through");
        Objects.requireNonNull(angle, "angle");
        points = List.copyOf(points);
    }
}
