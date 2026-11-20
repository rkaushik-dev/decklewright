package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.plan.rules.Phase;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import io.github.rkaushikdev.decklewright.plan.rules.RuleContext;
import io.github.rkaushikdev.decklewright.plan.rules.Rules;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Running a rule set.
 *
 * <p>The linear planner ran each step once in a fixed order, and that is what it could not fix:
 * snapping after an aspect breaks the aspect, and forcing the aspect after snapping breaks the snap.
 * Both collections exist and both were right.
 *
 * <p>The engine runs the shape and grid rules again until the crop stops changing. A set that
 * settles gives an answer both rules agree with. A set that does not settle is a contradiction, and
 * the plan says so rather than handing back whichever rule happened to run last.
 *
 * <p>The anchor rules run exactly once, before any of that. They say where the crop starts, and they
 * say it by measuring from the anchor rather than from the crop as it stands, so running one a
 * second time would put the crop back where it began and undo everything after it. That was found
 * the hard way: a bleed and a snap together never settled, because each pass the bleed reset what
 * the snap had just done.
 *
 * <p>The check rules run exactly once too, after everything has settled.
 */
public final class Engine {
    /** What the engine calls the anchor it was handed, until an anchor rule names it. */
    private static final String ANCHOR = "anchor";

    private Engine() {}

    /**
     * Runs the set over the plan, allowing six passes.
     *
     * @see #runRules(List, CropPlan, RuleContext, int)
     */
    public static RuleRun runRules(List<Rule> rules, CropPlan plan, RuleContext context) {
        return runRules(rules, plan, context, PlanOptions.DEFAULT_MAX_PASSES);
    }

    /**
     * Runs the set over the plan: the anchor rules once, then the shape and grid rules until a pass
     * changes nothing or the passes run out, then the check rules once. A set that runs out of passes
     * leaves a warning on the plan.
     *
     * @param context shared by every rule of the run
     * @param maxPasses how many times the shape and grid rules may run
     * @throws BadArgumentError when the pass limit is not above zero
     * @throws PlanError when two rules share a name, or a rule refuses the plan
     */
    public static RuleRun runRules(List<Rule> rules, CropPlan plan, RuleContext context, int maxPasses) {
        Guards.requirePositiveInteger(maxPasses, "maxPasses");
        Rules.requireNoDuplicates(rules);
        List<Rule> sorted = Rules.ordered(rules);
        List<Rule> anchors = Rules.inPhase(sorted, Phase.ANCHOR);
        List<Rule> moving = sorted.stream()
                .filter(item -> item.phase() == Phase.SHAPE || item.phase() == Phase.GRID)
                .toList();
        List<Rule> checks = Rules.inPhase(sorted, Phase.CHECK);

        CropPlan current = plan;
        int passes = 0;
        boolean settled = moving.isEmpty();

        for (Rule item : anchors) {
            current = item.apply(current, context);
        }

        for (int pass = 0; pass < maxPasses; pass += 1) {
            Rect before = current.rect();
            for (Rule item : moving) {
                current = item.apply(current, context);
            }
            passes += 1;
            if (current.rect().approxEquals(before)) {
                settled = true;
                break;
            }
        }

        if (!settled) {
            current = current.warned(
                    "the rules did not settle in " + passes + " passes, so the crop is the last one they produced");
        }

        for (Rule item : checks) {
            current = item.apply(current, context);
        }
        return new RuleRun(current, passes, settled);
    }

    /**
     * The plan the set makes from the input, allowing six passes.
     *
     * @see #planWith(List, PlanInput, int)
     */
    public static CropPlan planWith(List<Rule> rules, PlanInput input) {
        return planWith(rules, input, PlanOptions.DEFAULT_MAX_PASSES);
    }

    /**
     * The plan the set makes, starting at the anchor.
     *
     * @throws PlanError when the anchor has no area, or as {@link #runRules(List, CropPlan,
     *     RuleContext, int)} does
     * @throws BadArgumentError when the pass limit is not above zero
     */
    public static CropPlan planWith(List<Rule> rules, PlanInput input, int maxPasses) {
        return run(rules, input, maxPasses).plan();
    }

    /**
     * The same run as {@link #planWith(List, PlanInput)}, with the bookkeeping the explain command
     * wants, allowing six passes.
     *
     * @see #planInDetail(List, PlanInput, int)
     */
    public static PlannedCrop planInDetail(List<Rule> rules, PlanInput input) {
        return planInDetail(rules, input, PlanOptions.DEFAULT_MAX_PASSES);
    }

    /**
     * The same run as {@link #planWith(List, PlanInput, int)}, with the bookkeeping the explain
     * command wants: the passes, whether the set settled, and the names of the rules in the order they
     * ran.
     *
     * @throws PlanError as {@link #planWith(List, PlanInput, int)} does
     * @throws BadArgumentError when the pass limit is not above zero
     */
    public static PlannedCrop planInDetail(List<Rule> rules, PlanInput input, int maxPasses) {
        RuleRun out = run(rules, input, maxPasses);
        List<String> names = Rules.ordered(rules).stream().map(Rule::name).toList();
        return new PlannedCrop(out.plan(), out.passes(), out.settled(), names);
    }

    /**
     * The set without the rules of those names. An operator overriding one rule for one item should
     * not have to rebuild the set.
     */
    public static List<Rule> without(List<Rule> rules, List<String> names) {
        Set<String> drop = Set.copyOf(names);
        return rules.stream().filter(item -> !drop.contains(item.name())).toList();
    }

    /** The set with the rule of the same name replaced in place, or the rule added at the end when there is none. */
    public static List<Rule> withRule(List<Rule> rules, Rule replacement) {
        List<Rule> out = new ArrayList<>(rules);
        for (int at = 0; at < out.size(); at += 1) {
            if (out.get(at).name().equals(replacement.name())) {
                out.set(at, replacement);
                return List.copyOf(out);
            }
        }
        out.add(replacement);
        return List.copyOf(out);
    }

    /** Starts a plan at the anchor and runs the set over it, measuring from the anchor. */
    private static RuleRun run(List<Rule> rules, PlanInput input, int maxPasses) {
        Rect anchor = input.anchor();
        if (anchor.isEmpty()) {
            throw new PlanError("there is nothing to crop, the anchor has no area", Map.of("anchor", anchor.toJson()));
        }
        CropPlan start = CropPlan.start(input.capture(), anchor, input.sampling(), input.deskew());
        return runRules(rules, start, RuleContext.of(anchor, ANCHOR), maxPasses);
    }
}
