package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.QcError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * What a quality check is.
 *
 * <p>A check reads a capture and a set of limits and says whether it passed, what it measured, and
 * at what star level. The star level matters: the funder grades a collection by the worst check
 * across it, so a check that only says yes or no cannot answer the question anybody actually asks.
 *
 * <p>A check may also say it could not run, which is not the same as failing. A gray balance check
 * on a capture with no colour target has nothing to measure and saying it failed would be a lie.
 *
 * <p>A check is a name, what it {@link #needs()} from the {@link QcContext}, and a body that turns
 * the context into a {@link Measurement}. The standard ones are in {@code qc.checks}; a site that
 * wants another builds it with {@link #of(String, List, Function)} and adds it to the set it hands
 * the runner. Checks are immutable.
 */
public final class Check {
    /** The highest star level any check awards. */
    public static final int MAX_STARS = 4;

    /** The star floor a finding is held to when nobody names one: the house level. */
    static final int DEFAULT_MINIMUM_STARS = 3;

    private final String name;
    private final List<Need> needs;
    private final Function<QcContext, Measurement> body;

    private Check(String name, List<Need> needs, Function<QcContext, Measurement> body) {
        this.name = name;
        this.needs = needs;
        this.body = body;
    }

    /**
     * A check that needs nothing from the context.
     *
     * @see #of(String, List, Function)
     */
    public static Check of(String name, Function<QcContext, Measurement> run) {
        return of(name, List.of(), run);
    }

    /**
     * A check of the given parts.
     *
     * @param name what the check is called in a report, such as {@code noise}
     * @param needs what has to be in the context for the check to run; copied
     * @param run the measurement, given a context that holds everything named in {@code needs}
     * @throws io.github.rkaushikdev.decklewright.BadArgumentError when the name is empty
     * @throws QcError when there is no body to run
     */
    public static Check of(String name, List<Need> needs, Function<QcContext, Measurement> run) {
        Guards.requireNonEmptyString(name, "name");
        if (run == null) {
            throw new QcError("the " + name + " check has nothing to run", Map.of("name", name));
        }
        return new Check(name, List.copyOf(needs), run);
    }

    /** What the check is called in a report. */
    public String name() {
        return name;
    }

    /** What has to be in the context for the check to run, read only. */
    public List<Need> needs() {
        return needs;
    }

    /**
     * Measure, without the guards {@link #runCheck(Check, QcContext, int)} puts round it: the body
     * may throw, and may assume everything in {@link #needs()} is present.
     */
    public Measurement run(QcContext context) {
        return body.apply(context);
    }

    /** Like {@code Check[name=noise, needs=[surface, target, limits]]}. */
    @Override
    public String toString() {
        return "Check[name=" + name + ", needs=" + needs + "]";
    }

    /** What the context does not have of what was asked for, in the order asked. */
    public static List<Need> missingFrom(QcContext context, List<Need> needs) {
        return needs.stream().filter(need -> !context.has(need)).toList();
    }

    /**
     * Run one check against the house floor of three stars.
     *
     * @see #runCheck(Check, QcContext, int)
     */
    public static Finding runCheck(Check item, QcContext context) {
        return runCheck(item, context, DEFAULT_MINIMUM_STARS);
    }

    /**
     * Run one check, catching the two ways it can decline: something it needed was not there, or it
     * threw. Either way the run carries on, because a batch that stops on the first unmeasurable
     * capture is a batch nobody finishes.
     *
     * @param minimumStars the level the finding has to reach to pass
     * @return a finding that ran, or one that did not with the reason as its note
     */
    public static Finding runCheck(Check item, QcContext context, int minimumStars) {
        List<Need> missing = missingFrom(context, item.needs());
        if (!missing.isEmpty()) {
            List<String> labels = missing.stream().map(Need::label).toList();
            return declined(item.name(), "nothing to measure, no " + String.join(" or ", labels));
        }

        try {
            Measurement out = item.run(context);
            return new Finding(
                    item.name(),
                    true,
                    out.stars() >= minimumStars,
                    out.measured(),
                    out.stars(),
                    out.note(),
                    NumberText.of(minimumStars) + " stars");
        } catch (RuntimeException error) {
            return declined(item.name(), Objects.requireNonNullElse(error.getMessage(), ""));
        }
    }

    /**
     * Run every check against the house floor of three stars.
     *
     * @see #runChecks(List, QcContext, int)
     */
    public static List<Finding> runChecks(List<Check> checks, QcContext context) {
        return runChecks(checks, context, DEFAULT_MINIMUM_STARS);
    }

    /** One finding per check, in the order the checks were given. */
    public static List<Finding> runChecks(List<Check> checks, QcContext context, int minimumStars) {
        List<Finding> out = new ArrayList<>(checks.size());
        for (Check item : checks) {
            out.add(runCheck(item, context, minimumStars));
        }
        return List.copyOf(out);
    }

    /**
     * Stars for a measurement where lower is better.
     *
     * @see #starsFor(double, List, boolean)
     */
    public static int starsFor(double measured, List<Rung> ladder) {
        return starsFor(measured, ladder, false);
    }

    /**
     * Turn a measurement into stars against a ladder of limits, best first. The ladder is written
     * the way the guidelines write it, so a reader can check one against the other.
     *
     * @param higherIsBetter true when a rung is met by reaching its limit or more, as for signal to
     *     noise; false when it is met by staying at or under it
     * @return the stars of the first rung met; zero when none is, or the measurement is not a number
     */
    public static int starsFor(double measured, List<Rung> ladder, boolean higherIsBetter) {
        // an infinite reading is real: a patch with no variation at all has an
        // infinite signal to noise ratio and deserves the top rung, not none of them
        if (Double.isNaN(measured)) {
            return 0;
        }
        for (Rung rung : ladder) {
            boolean met = higherIsBetter ? measured >= rung.limit() : measured <= rung.limit();
            if (met) {
                return rung.stars();
            }
        }
        return 0;
    }

    /**
     * Refuses a set that holds two checks of the same name, since their findings could not be told
     * apart on a report.
     *
     * @throws QcError naming the first name seen twice
     */
    public static void requireNoDuplicates(List<Check> checks) {
        Set<String> seen = new HashSet<>();
        for (Check item : checks) {
            if (!seen.add(item.name())) {
                throw new QcError("the " + item.name() + " check is in the set twice", Map.of("name", item.name()));
            }
        }
    }

    private static Finding declined(String name, String note) {
        return new Finding(name, false, false, Double.NaN, 0, note, "");
    }
}
