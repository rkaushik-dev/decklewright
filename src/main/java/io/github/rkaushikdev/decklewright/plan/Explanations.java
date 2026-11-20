package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.PhysicalSizes;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Saying why a crop is where it is.
 *
 * <p>Every complaint about a crop is the same complaint: it is not where I expected. The trail
 * answers it, but only if it is printed in a way somebody can read at eight in the morning with two
 * hundred plates to get through.
 *
 * <p>The rules that changed nothing are left out. A trail that lists six rules and shows three
 * changes is easier to read than one that lists six rules and shows six, five of which did nothing.
 */
public final class Explanations {
    /** What the first step of every plan is called, which no explanation lists. */
    private static final String START = "start";

    private Explanations() {}

    /**
     * How a crop changed between two rects, such as {@code grew by 10 by 10, moved by -5 and -5}, or
     * {@code no change}.
     */
    public static String describeMove(Rect before, Rect after) {
        List<String> parts = new ArrayList<>();
        double dw = after.width() - before.width();
        double dh = after.height() - before.height();
        double dx = after.x() - before.x();
        double dy = after.y() - before.y();
        if (dw != 0 || dh != 0) {
            parts.add((dw >= 0 ? "grew" : "shrank")
                    + " by "
                    + NumberText.of(Math.abs(dw))
                    + " by "
                    + NumberText.of(Math.abs(dh)));
        }
        if (dx != 0 || dy != 0) {
            parts.add("moved by " + NumberText.of(dx) + " and " + NumberText.of(dy));
        }
        return parts.isEmpty() ? "no change" : String.join(", ", parts);
    }

    /**
     * One line per rule that moved the crop, in order, such as {@code bleed: kept 20 all round beyond
     * the anchor (grew by 40 by 40, moved by -20 and -20)}.
     */
    public static List<String> explainSteps(CropPlan plan) {
        return plan.steps().stream()
                .filter(step -> !step.rule().equals(START))
                .map(step -> step.rule() + ": " + step.note() + " (" + describeMove(step.before(), step.after()) + ")")
                .toList();
    }

    /**
     * The plan explained, with its physical size in inches.
     *
     * @see #explain(CropPlan, Unit)
     */
    public static List<String> explain(CropPlan plan) {
        return explain(plan, Unit.INCH);
    }

    /**
     * The plan explained: the crop and its frequency, its physical size in the unit (to two places in
     * inches and one in anything else), the skew taken out when there is one, the rules that moved
     * it, and every warning at the end.
     */
    public static List<String> explain(CropPlan plan, Unit unit) {
        return List.copyOf(lines(plan, unit));
    }

    /**
     * The explanation with the rule set alongside, in inches.
     *
     * @see #explainWithRules(CropPlan, List, Unit)
     */
    public static List<String> explainWithRules(CropPlan plan, List<Rule> rules) {
        return explainWithRules(plan, rules, Unit.INCH);
    }

    /**
     * The same explanation with the rule set alongside, so a curator can see the rules that ran and
     * did nothing as well as the ones that did something, and a note for each pair of rules in the
     * set that is known to argue.
     */
    public static List<String> explainWithRules(CropPlan plan, List<Rule> rules, Unit unit) {
        Set<String> moved = Set.copyOf(plan.trail());
        List<String> quiet = rules.stream()
                .map(Rule::name)
                .filter(name -> !moved.contains(name))
                .toList();
        List<String> lines = lines(plan, unit);
        if (!quiet.isEmpty()) {
            lines.add("rules that changed nothing: " + String.join(", ", quiet));
        }
        for (Tension tension : RuleSets.tensionsIn(rules)) {
            lines.add("note: " + tension.says());
        }
        return List.copyOf(lines);
    }

    /**
     * One line, for a batch log where two hundred of these go past, such as {@code 10x10+0+0 after
     * bleed then snap, 1 warnings}.
     */
    public static String summarise(CropPlan plan) {
        List<String> trail = plan.trail();
        String steps = trail.isEmpty() ? "nothing" : String.join(" then ", trail);
        String warned = plan.warnings().isEmpty() ? "" : ", " + plan.warnings().size() + " warnings";
        return plan.rect() + " after " + steps + warned;
    }

    /**
     * Which rule made the biggest difference to the area, which is nearly always the one somebody
     * wants to argue with. Of rules that made the same difference, the first wins.
     *
     * @return the rule and the area it changed, or empty when no rule moved the crop
     */
    public static Optional<Move> biggestMove(CropPlan plan) {
        Move best = null;
        for (CropPlan.Step step : plan.steps()) {
            if (step.rule().equals(START)) {
                continue;
            }
            double area = Math.abs(step.after().area() - step.before().area());
            if (best == null || area > best.area()) {
                best = new Move(step.rule(), area);
            }
        }
        return Optional.ofNullable(best);
    }

    /** The lines of {@link #explain(CropPlan, Unit)}, in a list the caller may add to. */
    private static List<String> lines(CropPlan plan, Unit unit) {
        Objects.requireNonNull(unit, "unit");
        List<String> lines = new ArrayList<>();
        lines.add("crop " + plan.rect() + " at " + plan.sampling());
        lines.add("which is " + PhysicalSizes.describeSize(plan.physical(unit), unit == Unit.INCH ? 2 : 1));
        if (!plan.deskew().isZero()) {
            lines.add("after taking out " + plan.deskew() + " of skew");
        }

        List<String> steps = explainSteps(plan);
        lines.add(steps.isEmpty() ? "no rule moved it" : steps.size() + " rules moved it:");
        for (String line : steps) {
            lines.add("  " + line);
        }

        for (String warning : plan.warnings()) {
            lines.add("warning: " + warning);
        }
        return lines;
    }

    /**
     * A rule and how much area it changed.
     *
     * @param rule the name of the rule
     * @param area the change in area, in square pixels, never negative
     */
    public record Move(String rule, double area) {

        /** A move of the given parts. */
        public Move {
            Objects.requireNonNull(rule, "rule");
        }
    }
}
