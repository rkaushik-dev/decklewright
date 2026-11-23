package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.store.Folder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the lab does with a result.
 *
 * <p>The grading says how good a capture is. The policy says what a lab does about it, and those
 * are different questions with different answers in different rooms. A digitisation grant with a
 * deadline delivers three star work and flags it. A programme rephotographing the only surviving
 * copy of a plat book does not, ever.
 *
 * <p>Every decision here is reversible by a person. Nothing is deleted, a rejection is a folder in
 * another place, and the reason is written down in words rather than as a code, because the person
 * who reads it will not have the code list.
 */
public final class Policies {

    /** What can be done with a finished job, in the order they are listed: deliver, reject, hold. */
    public static final List<Verdict> DECISIONS = List.of(Verdict.values());

    /**
     * The one a lab gets if it says nothing: three stars and above go out, ungraded work and a second
     * delivery of an accession are held, more than three warnings are held, a failure does not stop
     * the batch, a disputed {@code skewAgreement} is held whatever the stars, and no check is held to
     * a floor of its own.
     */
    public static final Policy HOUSE_POLICY = new Policy(3, false, false, 3, false, List.of("skewAgreement"), Map.of());

    private Policies() {}

    /** The house policy, to be changed with its {@code with} methods, each checked as it is made. */
    public static Policy policy() {
        return HOUSE_POLICY;
    }

    /**
     * The policy a profile implies, so a collection that asks for four stars in its quality section
     * does not have to say it twice: the house policy at the profile's level. A caller overrules the
     * profile with the policy's {@code with} methods.
     */
    public static Policy policyFor(Profile profile) {
        return HOUSE_POLICY.withMinimumStars(profile.qc().minimumStars());
    }

    /**
     * What to do with a finished job, under the house policy.
     *
     * @see #decide(DecisionInput, Policy)
     */
    public static Decision decide(DecisionInput input) {
        return decide(input, HOUSE_POLICY);
    }

    /**
     * What to do with a finished job.
     *
     * <p>The questions are asked in order and the first that settles it wins. A delivery that is
     * already there is held unless the policy may replace it. Ungraded work is held unless the policy
     * delivers it. A required check that could not be measured holds it, and so does a check on the
     * policy's hold list that ran and came in under the level, whatever the stars: the cases that
     * reach a person are the ones where the measurements disagree with each other rather than the
     * ones where they agree that something is bad. Then work under the level is rejected, and so is
     * work with a check under a floor the policy holds that check to, both before the warnings are
     * counted, because bad work is bad either way, and work with more warnings than the policy allows
     * is held. Everything else is delivered.
     */
    public static Decision decide(DecisionInput input, Policy rules) {
        for (Question question : QUESTIONS) {
            Optional<Decision> settled = question.ask(input, rules);
            if (settled.isPresent()) {
                return settled.get();
            }
        }
        QcResult quality = input.quality().orElseThrow();
        return new Decision(Verdict.DELIVER, quality.stars() + " of 4 stars", Folder.DELIVERED);
    }

    /** One of the questions a finished job is put to; empty when it does not settle the job. */
    @FunctionalInterface
    private interface Question {
        Optional<Decision> ask(DecisionInput input, Policy rules);
    }

    /** The questions, in the order {@link #decide(DecisionInput, Policy)} asks them. */
    private static final List<Question> QUESTIONS = List.of(
            Policies::alreadyThere,
            Policies::ungraded,
            Policies::unmeasured,
            Policies::disputed,
            Policies::underLevel,
            Policies::underFloor,
            Policies::tooManyWarnings);

    private static Optional<Decision> alreadyThere(DecisionInput input, Policy rules) {
        return input.alreadyThere() && !rules.overwrite()
                ? Optional.of(new Decision(Verdict.HOLD, "this accession has already been delivered", Folder.WORKING))
                : Optional.empty();
    }

    private static Optional<Decision> ungraded(DecisionInput input, Policy rules) {
        if (input.quality().isPresent()) {
            return Optional.empty();
        }
        return Optional.of(
                rules.deliverUngraded()
                        ? new Decision(Verdict.DELIVER, "nothing was graded and the policy allows it", Folder.DELIVERED)
                        : new Decision(Verdict.HOLD, "nothing was graded", Folder.WORKING));
    }

    private static Optional<Decision> unmeasured(DecisionInput input, Policy rules) {
        List<String> missing = input.quality().orElseThrow().missing();
        return missing.isEmpty()
                ? Optional.empty()
                : Optional.of(new Decision(
                        Verdict.HOLD, String.join(" and ", missing) + " could not be measured", Folder.WORKING));
    }

    private static Optional<Decision> disputed(DecisionInput input, Policy rules) {
        QcResult quality = input.quality().orElseThrow();
        List<String> disputed = rules.holdOn().stream()
                .filter(name -> ranUnder(quality, name, rules.minimumStars()))
                .toList();
        return disputed.isEmpty()
                ? Optional.empty()
                : Optional.of(new Decision(
                        Verdict.HOLD,
                        String.join(" and ", disputed) + " says somebody should look at this",
                        Folder.WORKING));
    }

    private static Optional<Decision> underLevel(DecisionInput input, Policy rules) {
        QcResult quality = input.quality().orElseThrow();
        if (quality.stars() >= rules.minimumStars()) {
            return Optional.empty();
        }
        String reason = quality.stars() + " of " + rules.minimumStars() + " stars, held down by "
                + quality.worst().orElse("nothing named");
        return Optional.of(new Decision(Verdict.REJECT, reason, Folder.REJECTED));
    }

    private static Optional<Decision> underFloor(DecisionInput input, Policy rules) {
        QcResult quality = input.quality().orElseThrow();
        List<String> under = rules.floors().entrySet().stream()
                .filter(floor -> ranUnder(quality, floor.getKey(), floor.getValue()))
                .map(floor -> floor.getKey() + " under the " + floor.getValue() + " stars this lab holds it to")
                .toList();
        return under.isEmpty()
                ? Optional.empty()
                : Optional.of(new Decision(Verdict.REJECT, String.join(" and ", under), Folder.REJECTED));
    }

    private static Optional<Decision> tooManyWarnings(DecisionInput input, Policy rules) {
        int warnings = input.warnings().size();
        if (warnings <= rules.maximumWarnings()) {
            return Optional.empty();
        }
        String reason = warnings + " warnings, more than the " + rules.maximumWarnings() + " this lab allows";
        return Optional.of(new Decision(Verdict.HOLD, reason, Folder.WORKING));
    }

    /** True when the first finding of the name ran and came in under the level. */
    private static boolean ranUnder(QcResult quality, String name, int level) {
        return quality.findings().stream()
                .filter(finding -> finding.name().equals(name))
                .findFirst()
                .filter(Finding::ran)
                .filter(finding -> finding.stars() < level)
                .isPresent();
    }

    /** Whether a batch carries on after a job failed: always, unless the policy stops on a failure. */
    public static boolean keepGoing(Policy rules, int failedSoFar) {
        return !rules.stopOnFailure() || failedSoFar == 0;
    }

    /** What a policy does, in words, one rule to a line. */
    public static List<String> describePolicy(Policy rules) {
        List<String> lines = new ArrayList<>();
        lines.add("delivers at " + rules.minimumStars() + " of 4 stars and above");
        lines.add(rules.deliverUngraded() ? "delivers ungraded work" : "holds ungraded work");
        lines.add(rules.overwrite() ? "replaces a delivery that is there" : "holds rather than replacing");
        lines.add("holds anything with more than " + rules.maximumWarnings() + " warnings");
        if (!rules.holdOn().isEmpty()) {
            lines.add("holds on " + String.join(", ", rules.holdOn()));
        }
        rules.floors().forEach((check, stars) -> lines.add("rejects " + check + " under " + stars + " stars"));
        lines.add(rules.stopOnFailure() ? "stops the batch on a failure" : "carries on past a failure");
        return List.copyOf(lines);
    }
}
