package io.github.rkaushikdev.decklewright.plan;

import java.util.List;
import java.util.Objects;

/**
 * A plan with the bookkeeping the explain command and the pipeline want: whether the rules settled,
 * how long they took, and which rules ran.
 *
 * @param plan the crop, its trail and its warnings
 * @param passes how many times the shape and grid rules ran
 * @param settled true when a pass changed nothing; false means the crop is merely the last one the
 *     rules produced, and the plan carries a warning saying so
 * @param rules the names of the rules that ran, in the order they ran; copied
 * @see CropPlanner#planCrop(io.github.rkaushikdev.decklewright.detect.CaptureReading,
 *     io.github.rkaushikdev.decklewright.profile.Profile, io.github.rkaushikdev.decklewright.geom.Rect)
 */
public record PlannedCrop(CropPlan plan, int passes, boolean settled, List<String> rules) {

    /** A planned crop of the given parts; the rule names are copied. */
    public PlannedCrop {
        Objects.requireNonNull(plan, "plan");
        rules = List.copyOf(rules);
    }

    /** The same bookkeeping around another plan. */
    PlannedCrop withPlan(CropPlan other) {
        return new PlannedCrop(other, passes, settled, rules);
    }
}
