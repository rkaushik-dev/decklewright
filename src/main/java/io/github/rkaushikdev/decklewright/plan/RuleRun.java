package io.github.rkaushikdev.decklewright.plan;

import java.util.Objects;

/**
 * What running a rule set gave.
 *
 * @param plan the plan the rules left, warnings included
 * @param passes how many times the shape and grid rules ran
 * @param settled true when a pass changed nothing, so every rule agrees with the crop
 * @see Engine#runRules(java.util.List, CropPlan, io.github.rkaushikdev.decklewright.plan.rules.RuleContext)
 */
public record RuleRun(CropPlan plan, int passes, boolean settled) {

    /** A run of the given parts. */
    public RuleRun {
        Objects.requireNonNull(plan, "plan");
    }
}
