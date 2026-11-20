package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.Objects;

/**
 * Turning a planned crop into whole pixels.
 *
 * <p>The rule engine works in real numbers because bleed of three millimetres at four hundred and
 * eighteen ppi is not a whole number of pixels. Exactly one step turns that into a crop the raster
 * layer can take, and which way it rounds is a collection decision, not an implementation detail.
 */
public final class Rounding {
    private Rounding() {}

    /** Every edge rounded to the {@linkplain RoundPolicy#NEAREST nearest} whole pixel. */
    public static Rect roundRect(Rect rect) {
        return roundRect(rect, RoundPolicy.NEAREST);
    }

    /** Every edge rounded the way the policy says. An extent the rounding would invert stays at zero instead. */
    public static Rect roundRect(Rect rect, RoundPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        double left = policy.low(rect.left());
        double top = policy.low(rect.top());
        double right = policy.high(rect.right());
        double bottom = policy.high(rect.bottom());
        return Rect.fromEdges(left, top, Math.max(left, right), Math.max(top, bottom));
    }

    /**
     * Both extents snapped to the {@linkplain SnapDirection#NEAREST nearest} multiple.
     *
     * @throws BadArgumentError when the multiple is not a whole number above zero
     */
    public static Size snapSize(Size size, double multiple) {
        return snapSize(size, multiple, SnapDirection.NEAREST);
    }

    /**
     * Forces the extents onto a multiple, which the tiled deliverables need because the viewer
     * wants every level to divide cleanly.
     *
     * @throws BadArgumentError when the multiple is not a whole number above zero
     */
    public static Size snapSize(Size size, double multiple, SnapDirection direction) {
        Guards.requirePositiveInteger(multiple, "multiple");
        Objects.requireNonNull(direction, "direction");
        return new Size(
                Math.max(0, direction.snap(size.width(), multiple)),
                Math.max(0, direction.snap(size.height(), multiple)));
    }

    /**
     * The extents of a crop snapped to the {@linkplain SnapDirection#NEAREST nearest} multiple.
     *
     * @throws BadArgumentError when the multiple is not a whole number above zero
     * @see #snapRect(Rect, double, SnapDirection)
     */
    public static Rect snapRect(Rect rect, double multiple) {
        return snapRect(rect, multiple, SnapDirection.NEAREST);
    }

    /**
     * Snaps the extents of a crop while keeping its centre where the planner put it. The origin is
     * then rounded to whole pixels, halves to even, because a half pixel origin would need a
     * resample the profile did not ask for.
     *
     * @throws BadArgumentError when the multiple is not a whole number above zero
     */
    public static Rect snapRect(Rect rect, double multiple, SnapDirection direction) {
        Size size = snapSize(rect.size(), multiple, direction);
        Rect centred = Rect.fromCentre(rect.centre(), size);
        return new Rect(Num.roundHalfEven(centred.x()), Num.roundHalfEven(centred.y()), size.width(), size.height());
    }

    /**
     * Whole pixels with even extents, grown where needed, which is what the jpeg service copies
     * need so the chroma subsampling does not have to pad the last column.
     */
    public static Rect evenRect(Rect rect) {
        return snapRect(rect, 2, SnapDirection.UP);
    }

    /** True when position and extent are all whole numbers, which separates a rendered crop from a planned one. */
    public static boolean isWholePixel(Rect rect) {
        return isWhole(rect.x()) && isWhole(rect.y()) && isWhole(rect.width()) && isWhole(rect.height());
    }

    private static boolean isWhole(double value) {
        return value == Math.rint(value);
    }
}
