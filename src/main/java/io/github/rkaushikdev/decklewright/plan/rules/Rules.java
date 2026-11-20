package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a crop rule is, and the few things done to a set of them.
 *
 * <p>A rule takes the plan as it stands and hands back the plan it wants. It does not know what ran
 * before it or what runs after, and it must be safe to run twice, because the engine runs the set
 * until it stops changing.
 *
 * <p>The phase is the only ordering a rule gets to ask for. Four of them, and they are enough:
 *
 * <ul>
 *   <li>{@code anchor}: what the crop is measured from
 *   <li>{@code shape}: what shape it should be, before anything is rounded
 *   <li>{@code grid}: whole pixels, multiples, and the other machine constraints
 *   <li>{@code check}: nothing moves, only complaints
 * </ul>
 */
public final class Rules {
    /** Every phase, in the order the engine runs them. */
    public static final List<Phase> PHASES = List.of(Phase.values());

    private Rules() {}

    /**
     * A rule of the given name, phase and body.
     *
     * @throws BadArgumentError when the name is empty or the phase is missing
     * @throws PlanError when there is no body to apply
     */
    public static Rule rule(String name, Phase phase, Rule.Body apply) {
        return new Rule(name, phase, apply);
    }

    /** True when the value is a rule. */
    public static boolean isRule(Object value) {
        return value instanceof Rule;
    }

    /**
     * The rules of one phase, in the order given.
     *
     * @throws BadArgumentError when the phase is missing
     */
    public static List<Rule> inPhase(List<Rule> rules, Phase phase) {
        Guards.requireOneOf(phase, PHASES, "phase");
        return rules.stream().filter(item -> item.phase() == phase).toList();
    }

    /**
     * Sorted by phase, keeping the order rules were given within a phase. A rule set is written in
     * the order a curator thinks about it and that order is worth keeping where it does not conflict
     * with the phases.
     */
    public static List<Rule> ordered(List<Rule> rules) {
        return rules.stream().sorted(Comparator.comparing(Rule::phase)).toList();
    }

    /**
     * Throws when two rules in the set share a name.
     *
     * @throws PlanError naming the first name that is in the set twice
     */
    public static void requireNoDuplicates(List<Rule> rules) {
        Set<String> seen = new HashSet<>();
        for (Rule item : rules) {
            if (!seen.add(item.name())) {
                throw new PlanError("the rule " + item.name() + " is in the set twice", Map.of("name", item.name()));
            }
        }
    }
}
