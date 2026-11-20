package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Objects;

/**
 * One connected blob of set pixels: the sheet, the colour target, the scale bar or a fleck of dust.
 *
 * @param id the blob's label, counting from one in the order the blobs were met scanning row by
 *     row; zero is never a blob
 * @param area how many pixels the blob holds
 * @param bounds the smallest whole pixel rect holding every pixel of the blob
 * @param centroid the mean position of the blob's pixels
 * @param touchesEdge true when a pixel of the blob lies on the edge of the capture
 * @see Components#labelComponents(Mask)
 */
public record Component(int id, int area, Rect bounds, Point centroid, boolean touchesEdge) {
    /** A component of the given parts. */
    public Component {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(centroid, "centroid");
    }
}
