package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.Map;

/**
 * One crop rule: a name, a {@link Phase}, and what it does to a plan.
 *
 * <p>A rule takes the plan as it stands and hands back the plan it wants. It does not know what ran
 * before it or what runs after, and it must be safe to run twice, because the engine runs the set
 * until it stops changing. The name is what the trail, the explanations and an operator's override
 * all call it by, so two rules in one set never share a name.
 *
 * <p>A rule is immutable. Two rules are the same rule only when they are the same instance.
 *
 * @see Rules#rule(String, Phase, Body)
 */
public final class Rule {
    private final String name;
    private final Phase phase;
    private final Body body;

    /**
     * A rule of the given parts.
     *
     * @throws BadArgumentError when the name is empty or the phase is missing
     * @throws PlanError when there is no body to apply
     */
    Rule(String name, Phase phase, Body body) {
        Guards.requireNonEmptyString(name, "name");
        Guards.requireOneOf(phase, Rules.PHASES, "phase");
        if (body == null) {
            throw new PlanError("the " + name + " rule has nothing to apply", Map.of("name", name));
        }
        this.name = name;
        this.phase = phase;
        this.body = body;
    }

    /** What the trail calls this rule, such as {@code bleed}. */
    public String name() {
        return name;
    }

    /** When the engine runs it. */
    public Phase phase() {
        return phase;
    }

    /**
     * The plan this rule wants, given the plan as it stands. A rule that changes nothing hands back
     * the very plan it was given.
     *
     * @param context what the anchor rules recorded for the rules after them
     */
    public CropPlan apply(CropPlan plan, RuleContext context) {
        return body.apply(plan, context);
    }

    /** The phase and the name, such as {@code grid: snap}, which is how a rule set is listed. */
    @Override
    public String toString() {
        return phase + ": " + name;
    }

    /** What a rule does: the plan it wants, given the plan as it stands. */
    @FunctionalInterface
    public interface Body {
        /**
         * The plan this rule wants.
         *
         * @param plan the plan as it stands
         * @param context what the anchor rules recorded for the rules after them
         */
        CropPlan apply(CropPlan plan, RuleContext context);
    }
}
