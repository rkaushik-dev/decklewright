package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import java.util.Objects;

/**
 * Bleed: paper kept beyond whatever the crop is anchored to.
 *
 * <p>It runs in the anchor phase because it defines where the crop starts, not what shape it is. A
 * profile with no bleed leaves the anchor exactly where the detector put it, which is the common case
 * and costs nothing.
 *
 * <p>Bleed is idempotent by construction: it is applied to the anchor rect held in the context,
 * never to the crop as it stands, so running it twice gives the same answer as running it once.
 */
public final class BleedRules {
    /** What every bleed rule is called in the trail. */
    private static final String NAME = "bleed";

    private BleedRules() {}

    /**
     * Pushes the anchor out by the bleed, wherever that lands.
     *
     * @param bleed in pixels
     */
    public static Rule bleedRule(Insets bleed) {
        Objects.requireNonNull(bleed, "bleed");
        return Rules.rule(NAME, Phase.ANCHOR, (plan, context) -> {
            if (bleed.isZero()) {
                return plan;
            }
            Rect from = anchorOf(plan, context);
            return plan.moved(
                    bleed.expandFrom(from),
                    NAME,
                    "kept " + bleed + " beyond the " + context.anchorName().orElse("anchor"));
        });
    }

    /**
     * A bleed that is trimmed so it cannot reach off the capture. Used by the collections that would
     * rather lose a millimetre of paper than have a black strip down one edge of a master. It says
     * so when it trims.
     *
     * @param bleed in pixels
     */
    public static Rule trimmedBleedRule(Insets bleed) {
        Objects.requireNonNull(bleed, "bleed");
        return Rules.rule(NAME, Phase.ANCHOR, (plan, context) -> {
            if (bleed.isZero()) {
                return plan;
            }
            Rect wanted = bleed.expandFrom(anchorOf(plan, context));
            Rect trimmed = wanted.intersect(plan.capture());
            double lost = wanted.area() - trimmed.area();
            CropPlan moved = plan.moved(
                    trimmed,
                    NAME,
                    lost > 0
                            ? "kept " + bleed + " beyond the anchor, trimmed to the capture"
                            : "kept " + bleed + " beyond the anchor");
            return lost > 0 ? moved.warned("the bleed was trimmed at the edge of the capture") : moved;
        });
    }

    /**
     * The bleed a profile asked for, less whatever the paper does not have. A neatline anchor sits
     * inside the paper, so a profile that wants three millimetres of paper beyond the neatline is
     * asking for something the sheet may not have. It says so when the sheet falls short.
     *
     * @param bleed in pixels
     * @param sheet the paper edge
     */
    public static Rule bleedWithinSheetRule(Insets bleed, Rect sheet) {
        Objects.requireNonNull(bleed, "bleed");
        Objects.requireNonNull(sheet, "sheet");
        return Rules.rule(NAME, Phase.ANCHOR, (plan, context) -> {
            if (bleed.isZero()) {
                return plan;
            }
            Rect wanted = bleed.expandFrom(anchorOf(plan, context));
            Rect held = wanted.intersect(sheet);
            double shortfall = wanted.area() - held.area();
            CropPlan moved = plan.moved(held, NAME, "kept " + bleed + " beyond the anchor, held inside the sheet");
            return shortfall > 0 ? moved.warned("the sheet does not have enough paper for the bleed asked for") : moved;
        });
    }

    /**
     * The plain bleed.
     *
     * @see #bleedFor(Insets, BleedMode, Rect)
     */
    public static Rule bleedFor(Insets bleed) {
        return bleedFor(bleed, BleedMode.PLAIN);
    }

    /**
     * The bleed the mode names, with no sheet to hold it in, so {@link BleedMode#WITHIN_SHEET} falls
     * back to the plain bleed.
     *
     * @see #bleedFor(Insets, BleedMode, Rect)
     */
    public static Rule bleedFor(Insets bleed, BleedMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case TRIMMED -> trimmedBleedRule(bleed);
            case PLAIN, WITHIN_SHEET -> bleedRule(bleed);
        };
    }

    /**
     * The bleed the mode names: {@linkplain #bleedRule(Insets) plain}, {@linkplain
     * #trimmedBleedRule(Insets) trimmed} at the capture, or {@linkplain #bleedWithinSheetRule(Insets,
     * Rect) held} inside the sheet.
     */
    public static Rule bleedFor(Insets bleed, BleedMode mode, Rect sheet) {
        Objects.requireNonNull(sheet, "sheet");
        return mode == BleedMode.WITHIN_SHEET ? bleedWithinSheetRule(bleed, sheet) : bleedFor(bleed, mode);
    }

    /** A bleed of nothing, which leaves every plan as it is. */
    public static Rule noBleedRule() {
        return bleedRule(Insets.zero());
    }

    /** The anchor in the context, or the crop as it stands when there is none. */
    private static Rect anchorOf(CropPlan plan, RuleContext context) {
        return context.anchor().orElse(plan.rect());
    }
}
