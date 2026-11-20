package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Objects;

/**
 * The safe area: paper the crop must not include.
 *
 * <p>A collection with a bound edge does not want the gutter in the plate, and a collection whose
 * sheets were stamped in one corner does not want the stamp. The safe area is written per edge for
 * exactly that reason.
 *
 * <p>It runs in the shape phase, after the anchor and the bleed have settled where the crop starts,
 * and it is idempotent because it works from the anchor held in the context rather than from
 * wherever the crop has got to.
 */
public final class SafeAreaRules {
    /** What every safe area rule is called in the trail. */
    private static final String NAME = "safeArea";

    /** What a safe area that leaves nothing says. */
    private static final String CONSUMED = "the safe area leaves no crop at all";

    private SafeAreaRules() {}

    /**
     * Pulls the crop in to the area inside the anchor. A safe area that uses the whole anchor up
     * leaves no crop, and says so.
     *
     * @param safeArea in pixels
     */
    public static Rule safeAreaRule(Insets safeArea) {
        Objects.requireNonNull(safeArea, "safeArea");
        return Rules.rule(NAME, Phase.SHAPE, (plan, context) -> {
            if (safeArea.isZero()) {
                return plan;
            }
            Rect from = context.anchor().orElse(plan.rect());
            Rect wanted = safeArea.applyTo(from);
            if (wanted.isEmpty()) {
                return plan.warned(CONSUMED)
                        .moved(wanted, NAME, "pulled in by " + safeArea + ", which consumed the anchor");
            }
            Rect held = plan.rect().intersect(wanted);
            return plan.moved(held.isEmpty() ? wanted : held, NAME, "pulled in by " + safeArea);
        });
    }

    /**
     * A safe area that only pulls in on the edges it was given, leaving the rest of the crop wherever
     * the bleed put it. This is the one the bound volumes use: a gutter on the left and nothing
     * anywhere else. Edges that would meet or cross leave the crop where it was, with a warning.
     *
     * @param safeArea in pixels; an edge of zero or less is left alone
     */
    public static Rule edgeSafeAreaRule(Insets safeArea) {
        Objects.requireNonNull(safeArea, "safeArea");
        return Rules.rule(NAME, Phase.SHAPE, (plan, context) -> {
            if (safeArea.isZero()) {
                return plan;
            }
            Rect crop = plan.rect();
            Rect limit = safeArea.applyTo(context.anchor().orElse(crop));
            double left = safeArea.left() > 0 ? Math.max(crop.left(), limit.left()) : crop.left();
            double top = safeArea.top() > 0 ? Math.max(crop.top(), limit.top()) : crop.top();
            double right = safeArea.right() > 0 ? Math.min(crop.right(), limit.right()) : crop.right();
            double bottom = safeArea.bottom() > 0 ? Math.min(crop.bottom(), limit.bottom()) : crop.bottom();
            if (right <= left || bottom <= top) {
                return plan.warned(CONSUMED);
            }
            return plan.moved(Rect.fromEdges(left, top, right, bottom), NAME, "held off the edges by " + safeArea);
        });
    }

    /**
     * The safe area that pulls in on every edge.
     *
     * @see #safeAreaFor(Insets, SafeAreaMode)
     */
    public static Rule safeAreaFor(Insets safeArea) {
        return safeAreaFor(safeArea, SafeAreaMode.WHOLE);
    }

    /** The safe area rule the mode names. */
    public static Rule safeAreaFor(Insets safeArea, SafeAreaMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case WHOLE -> safeAreaRule(safeArea);
            case EDGES -> edgeSafeAreaRule(safeArea);
        };
    }
}
