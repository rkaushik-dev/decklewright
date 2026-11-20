package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.ops.Rotation;

/**
 * The mapping from raw capture coordinates into the straightened frame, plus the canvas that frame
 * has.
 *
 * <p>The rotation is about the centre of the capture and the canvas grows, so the offset of the new
 * canvas is in the mapping too: the centre of the capture lands on the centre of the canvas, and the
 * canvas sits at the origin. With no skew the mapping is the identity and the canvas is the capture
 * itself.
 *
 * @see CropPlanner#straightFrame(Rect, Angle)
 */
public final class StraightFrame {
    private final Rect capture;
    private final boolean identity;
    private final double cx;
    private final double cy;
    private final double cos;
    private final double sin;
    private final double offsetX;
    private final double offsetY;

    private StraightFrame(
            Rect capture,
            boolean identity,
            double cx,
            double cy,
            double cos,
            double sin,
            double offsetX,
            double offsetY) {
        this.capture = capture;
        this.identity = identity;
        this.cx = cx;
        this.cy = cy;
        this.cos = cos;
        this.sin = sin;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /** The frame a capture turned by the skew straightens into. */
    static StraightFrame of(Rect capture, Angle skew) {
        if (skew.isZero()) {
            return new StraightFrame(capture, true, 0, 0, 1, 0, 0, 0);
        }
        Rect grown = Rotation.rotatedBounds(capture, skew.negated());
        double radians = -skew.radians();
        return new StraightFrame(
                grown.translated(-grown.left(), -grown.top()),
                false,
                capture.centre().x(),
                capture.centre().y(),
                StrictMath.cos(radians),
                StrictMath.sin(radians),
                (grown.width() - capture.width()) / 2,
                (grown.height() - capture.height()) / 2);
    }

    /** Where a point of the raw capture lands once the capture is straightened. */
    public Point point(double x, double y) {
        if (identity) {
            return new Point(x, y);
        }
        double dx = x - cx;
        double dy = y - cy;
        return new Point(cx + dx * cos - dy * sin + offsetX, cy + dx * sin + dy * cos + offsetY);
    }

    /** The canvas of the straightened frame, at the origin; the raw capture when there is no skew. */
    public Rect capture() {
        return capture;
    }
}
