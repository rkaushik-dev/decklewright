package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.PipelineError;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a lab does with a result, as rules a run follows.
 *
 * <p>Every rule is checked when the policy is made, so a policy that exists is one a run can
 * follow, and it cannot be changed once a run has it: each {@code with} method gives back a new one.
 *
 * @param minimumStars below this it does not go out; a whole number from zero to four
 * @param deliverUngraded whether a run with no grading may deliver
 * @param overwrite whether to replace a delivery that is already there
 * @param maximumWarnings above this it is held for a person; never negative
 * @param stopOnFailure whether one bad sheet stops the batch
 * @param holdOn checks that send it to a person whatever the stars; copied
 * @param floors checks held to a level of their own, by name, above the delivery's level; copied
 * @see Policies#policy()
 * @see Policies#decide(DecisionInput, Policy)
 */
public record Policy(
        int minimumStars,
        boolean deliverUngraded,
        boolean overwrite,
        int maximumWarnings,
        boolean stopOnFailure,
        List<String> holdOn,
        Map<String, Integer> floors) {

    /** The highest level on the ladder. */
    private static final int TOP_STARS = 4;

    /**
     * A policy of the given rules; the hold list is copied.
     *
     * @throws PipelineError when the star level is off the ladder or the warning count is negative;
     *     the details carry the value refused
     */
    public Policy {
        requireStarLevel(minimumStars);
        if (maximumWarnings < 0) {
            throw new PipelineError("a warning count cannot be negative", Map.of("maximumWarnings", maximumWarnings));
        }
        holdOn = List.copyOf(holdOn);
        floors.values().forEach(Policy::requireStarLevel);
        floors = Collections.unmodifiableMap(new LinkedHashMap<>(floors));
    }

    /**
     * This policy delivering at another level.
     *
     * <p>The level is taken as a number, so a level typed at a terminal is checked here, where the
     * ladder is defined, rather than rounded on its way in.
     *
     * @param stars a whole number from zero to four
     * @throws PipelineError when it is not; the details carry it
     */
    public Policy withMinimumStars(double stars) {
        requireStarLevel(stars);
        return new Policy((int) stars, deliverUngraded, overwrite, maximumWarnings, stopOnFailure, holdOn, floors);
    }

    /** This policy delivering ungraded work or holding it. */
    public Policy withDeliverUngraded(boolean deliver) {
        return new Policy(minimumStars, deliver, overwrite, maximumWarnings, stopOnFailure, holdOn, floors);
    }

    /** This policy replacing a delivery that is already there, or holding rather than replacing. */
    public Policy withOverwrite(boolean replace) {
        return new Policy(minimumStars, deliverUngraded, replace, maximumWarnings, stopOnFailure, holdOn, floors);
    }

    /**
     * This policy holding anything with more warnings than the given count.
     *
     * @throws PipelineError when the count is negative
     */
    public Policy withMaximumWarnings(int count) {
        return new Policy(minimumStars, deliverUngraded, overwrite, count, stopOnFailure, holdOn, floors);
    }

    /** This policy stopping the batch on a failure, or carrying on past one. */
    public Policy withStopOnFailure(boolean stop) {
        return new Policy(minimumStars, deliverUngraded, overwrite, maximumWarnings, stop, holdOn, floors);
    }

    /** This policy holding on other checks; the list is copied. */
    public Policy withHoldOn(List<String> checks) {
        return new Policy(minimumStars, deliverUngraded, overwrite, maximumWarnings, stopOnFailure, checks, floors);
    }

    /**
     * This policy holding one check to a level of its own, on top of any it already holds. A
     * quadrangle programme that delivers at three stars may still want sharpness at four, because a
     * soft contour line is the one fault the users write in about.
     *
     * @param stars a whole number from zero to four
     * @throws PipelineError when it is not; the details carry it
     */
    public Policy withFloor(String check, double stars) {
        requireStarLevel(stars);
        Map<String, Integer> more = new LinkedHashMap<>(floors);
        more.put(check, (int) stars);
        return new Policy(minimumStars, deliverUngraded, overwrite, maximumWarnings, stopOnFailure, holdOn, more);
    }

    private static void requireStarLevel(double stars) {
        if (stars != Math.floor(stars) || stars < 0 || stars > TOP_STARS) {
            throw new PipelineError("a star level is a whole number from zero to four", Map.of("minimumStars", stars));
        }
    }
}
