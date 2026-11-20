package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What to do when the crop reaches off the capture.
 *
 * <p>It happens for two reasons and they want different answers. A sheet placed too near the edge of
 * the bed produces a crop that genuinely has no image in one corner, and the master has to be padded
 * so it comes out the size the plan said. A bleed that is a hair too wide produces a crop that is one
 * pixel over, and trimming it is fine.
 *
 * <p>The profile picks. Nothing here guesses, because the two look identical to the code and
 * completely different to a curator.
 */
public final class BoundsRules {
    /** Every mode, in the order a profile lists them. */
    public static final List<BoundsMode> BOUNDS_MODES = List.of(BoundsMode.values());

    /** What every bounds rule is called in the trail. */
    private static final String NAME = "bounds";

    private BoundsRules() {}

    /**
     * Says something and changes nothing, which is what a collection that pads its masters wants:
     * the crop is the plan and the render fills the gap.
     */
    public static Rule warnOnOverhangRule() {
        return Rules.rule(NAME, Phase.CHECK, (plan, context) -> {
            if (!plan.overhangs()) {
                return plan;
            }
            return plan.warned(
                    NumberText.of(Num.roundHalfUp(plan.overhangArea())) + " px of the crop falls off the capture");
        });
    }

    /**
     * Cuts the crop back to what the capture holds. The output is smaller than the plan asked for,
     * which is why it says so.
     *
     * @throws PlanError from the rule when the crop does not touch the capture at all
     */
    public static Rule clampToCaptureRule() {
        return Rules.rule(NAME, Phase.GRID, (plan, context) -> {
            if (!plan.overhangs()) {
                return plan;
            }
            Rect held = plan.rect().intersect(plan.capture());
            if (held.isEmpty()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("rect", plan.rect().toJson());
                details.put("capture", plan.capture().toJson());
                throw new PlanError("the crop does not touch the capture at all", details);
            }
            return plan.moved(held, NAME, "trimmed to the capture")
                    .warned("the crop was trimmed at the edge of the capture");
        });
    }

    /**
     * Slides the crop back on, keeping its size. This is the one the tiled viewer collections use,
     * because a plate that is not the size the plan said breaks the tiling and a plate that is a few
     * pixels off centre does not. A crop larger than the capture is centred on it, with a warning.
     */
    public static Rule nudgeIntoCaptureRule() {
        return Rules.rule(NAME, Phase.GRID, (plan, context) -> {
            if (!plan.overhangs()) {
                return plan;
            }
            Rect moved = plan.rect().nudgedInto(plan.capture());
            boolean stillOver = !plan.capture().containsRect(moved);
            CropPlan out = plan.moved(moved, NAME, "slid back onto the capture");
            return stillOver ? out.warned("the crop is larger than the capture and was centred on it") : out;
        });
    }

    /**
     * Stops. Some collections would rather hold an item than deliver a padded master, and for those
     * a crop that runs off the bed means the capture has to be taken again.
     *
     * @throws PlanError from the rule when the crop reaches off the capture, with the rounded {@code
     *     overhang} in square pixels and the {@code rect} in the details
     */
    public static Rule refuseOverhangRule() {
        return Rules.rule(NAME, Phase.CHECK, (plan, context) -> {
            if (!plan.overhangs()) {
                return plan;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("overhang", Num.roundHalfUp(plan.overhangArea()));
            details.put("rect", plan.rect().toJson());
            throw new PlanError("the crop reaches off the capture and this collection will not pad", details);
        });
    }

    /**
     * The bounds rule that warns.
     *
     * @see #boundsFor(BoundsMode)
     */
    public static Rule boundsFor() {
        return boundsFor(BoundsMode.WARN);
    }

    /**
     * The bounds rule the mode names.
     *
     * @throws BadArgumentError when the mode is missing
     */
    public static Rule boundsFor(BoundsMode mode) {
        Guards.requireOneOf(mode, BOUNDS_MODES, "mode");
        return switch (mode) {
            case WARN -> warnOnOverhangRule();
            case CLAMP -> clampToCaptureRule();
            case NUDGE -> nudgeIntoCaptureRule();
            case REFUSE -> refuseOverhangRule();
        };
    }

    /**
     * A crop with no area is a fault whatever the collection thinks, because there is nothing to
     * write. It is separate from the bounds rules so it runs even when a profile turned those off.
     *
     * @throws PlanError from the rule when the crop has no area, with the {@code trail} that got
     *     there in the details
     */
    public static Rule notEmptyRule() {
        return Rules.rule("notEmpty", Phase.CHECK, (plan, context) -> {
            if (!plan.rect().isEmpty()) {
                return plan;
            }
            throw new PlanError("the rules between them left no crop at all", Map.of("trail", plan.trail()));
        });
    }
}
