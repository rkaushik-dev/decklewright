package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.qc.QcResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a decision about a finished job is made from.
 *
 * @param quality what the grading gave back; empty when the run did not grade
 * @param warnings what an operator should read before trusting the delivery; copied
 * @param alreadyThere whether the accession has already been delivered
 * @see Policies#decide(DecisionInput, Policy)
 */
public record DecisionInput(Optional<QcResult> quality, List<String> warnings, boolean alreadyThere) {

    /** An input of the given parts; the warnings are copied. */
    public DecisionInput {
        Objects.requireNonNull(quality, "quality");
        warnings = List.copyOf(warnings);
    }

    /** A graded job with no warnings that has not been delivered before. */
    public static DecisionInput graded(QcResult quality) {
        return new DecisionInput(Optional.of(quality), List.of(), false);
    }

    /** A job that was never graded, with no warnings, that has not been delivered before. */
    public static DecisionInput ungraded() {
        return new DecisionInput(Optional.empty(), List.of(), false);
    }

    /** This input with other warnings. */
    public DecisionInput withWarnings(List<String> others) {
        return new DecisionInput(quality, others, alreadyThere);
    }

    /** This input saying whether the accession has already been delivered. */
    public DecisionInput withAlreadyThere(boolean delivered) {
        return new DecisionInput(quality, warnings, delivered);
    }
}
