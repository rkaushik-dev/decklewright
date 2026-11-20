package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What one run over one accession did.
 *
 * <p>A run either stopped at a stage that failed, and then it carries that stage's error and no
 * decision, or every stage that had to run did, and then it carries the policy's decision.
 *
 * @param accession what the run was of
 * @param job the job as the last stage left it
 * @param results one per stage reached, in the order they ran; copied
 * @param error the first stage failure; empty when every stage that had to run did
 * @param decision what the policy decided; empty when a stage failed
 * @param ms how long it took, in whole milliseconds
 * @see Runner#runJob(JobInput, RunOptions)
 */
public record RunResult(
        String accession,
        Job job,
        List<StageResult> results,
        Optional<RuntimeException> error,
        Optional<Decision> decision,
        long ms) {

    /**
     * A result of the given parts; the stage results are copied.
     *
     * @throws BadArgumentError when it carries both an error and a decision, or neither
     */
    public RunResult {
        Objects.requireNonNull(accession, "accession");
        Objects.requireNonNull(job, "job");
        results = List.copyOf(results);
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(decision, "decision");
        Guards.require(
                error.isPresent() != decision.isPresent(),
                "a run carries an error or a decision, and only one",
                Map.of("accession", accession));
    }

    /** Whether every stage that had to run did. */
    public boolean ok() {
        return error.isEmpty();
    }
}
