package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.geom.Rounding;
import io.github.rkaushikdev.decklewright.geom.SnapDirection;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;

/**
 * Whole pixels, and multiples of them.
 *
 * <p>These are the last rules to run because everything before them works in real numbers. Bleed of
 * three millimetres at four hundred and eighteen ppi is not a whole number of pixels and pretending
 * otherwise earlier just moves the rounding somewhere less visible.
 *
 * <p>Snapping and rounding are separate rules on purpose. A collection that delivers into a tiled
 * viewer needs multiples of the tile and does not care where the crop starts. A collection that only
 * needs whole pixels should not pay for a snap it did not ask for.
 */
public final class SnapRules {
    /** What the snapping rules are called in the trail. */
    private static final String SNAP = "snap";

    private SnapRules() {}

    /**
     * Grows the extents up to the multiple.
     *
     * @see #snapRule(double, SnapDirection)
     */
    public static Rule snapRule(double multiple) {
        return snapRule(multiple, SnapDirection.UP);
    }

    /**
     * Snaps the extents of the crop onto the multiple, keeping its centre near where it was. A
     * multiple of one, or an empty crop, is left alone.
     *
     * @param multiple pixels, a whole number above zero
     * @throws BadArgumentError when the multiple is not a whole number above zero, or the direction
     *     is missing
     */
    public static Rule snapRule(double multiple, SnapDirection direction) {
        Guards.requirePositiveInteger(multiple, "multiple");
        Guards.requireOneOf(direction, List.of(SnapDirection.values()), "direction");
        String note = "snapped to " + NumberText.of(multiple) + " px " + direction.label();
        return Rules.rule(SNAP, Phase.GRID, (plan, context) -> {
            if (plan.rect().isEmpty() || multiple == 1) {
                return plan;
            }
            return plan.moved(Rounding.snapRect(plan.rect(), multiple, direction), SNAP, note);
        });
    }

    /**
     * Puts the crop on whole pixels the way the policy says. A crop already on whole pixels is left
     * alone.
     *
     * @throws BadArgumentError when the policy is missing
     */
    public static Rule roundRule(RoundPolicy policy) {
        Guards.requireOneOf(policy, List.of(RoundPolicy.values()), "policy");
        return Rules.rule("round", Phase.GRID, (plan, context) -> {
            if (Rounding.isWholePixel(plan.rect())) {
                return plan;
            }
            return plan.moved(Rounding.roundRect(plan.rect(), policy), "round", "rounded " + policy.label());
        });
    }

    /**
     * Snaps the origin as well as the extents, which the tiled viewer needs because it addresses
     * tiles by absolute position rather than by offset within a plate. The crop only ever grows.
     *
     * @param multiple pixels, a whole number above zero
     * @throws BadArgumentError when the multiple is not a whole number above zero
     */
    public static Rule gridAlignRule(double multiple) {
        Guards.requirePositiveInteger(multiple, "multiple");
        String note = "aligned to a " + NumberText.of(multiple) + " px grid";
        return Rules.rule("gridAlign", Phase.GRID, (plan, context) -> {
            if (plan.rect().isEmpty() || multiple == 1) {
                return plan;
            }
            Rect crop = plan.rect();
            double left = Math.floor(crop.left() / multiple) * multiple;
            double top = Math.floor(crop.top() / multiple) * multiple;
            double right = Math.ceil(crop.right() / multiple) * multiple;
            double bottom = Math.ceil(crop.bottom() / multiple) * multiple;
            return plan.moved(Rect.fromEdges(left, top, right, bottom), "gridAlign", note);
        });
    }

    /**
     * Even extents, which the jpeg service copies need so the chroma subsampling does not have to pad
     * the last column. It is a snap of two, named for what it is for.
     */
    public static Rule evenExtentsRule() {
        return Rules.rule(SNAP, Phase.GRID, (plan, context) -> {
            Rect crop = plan.rect();
            if (crop.isEmpty() || (crop.width() % 2 == 0 && crop.height() % 2 == 0)) {
                return plan;
            }
            return plan.moved(Rounding.snapRect(crop, 2, SnapDirection.UP), SNAP, "grown to even extents");
        });
    }

    /**
     * Grows the extents up to the multiple.
     *
     * @see #snapFor(double, SnapDirection, boolean)
     */
    public static Rule snapFor(double multiple) {
        return snapFor(multiple, SnapDirection.UP, false);
    }

    /**
     * Snaps the extents onto the multiple the given way, leaving the origin where it falls.
     *
     * @see #snapFor(double, SnapDirection, boolean)
     */
    public static Rule snapFor(double multiple, SnapDirection direction) {
        return snapFor(multiple, direction, false);
    }

    /**
     * The snap a collection asks for: the {@linkplain #gridAlignRule(double) grid align} when the
     * origin is to be aligned too, in which case the direction is not used, and the {@linkplain
     * #snapRule(double, SnapDirection) plain snap} otherwise.
     *
     * @throws BadArgumentError as the chosen rule does
     */
    public static Rule snapFor(double multiple, SnapDirection direction, boolean alignOrigin) {
        return alignOrigin ? gridAlignRule(multiple) : snapRule(multiple, direction);
    }
}
