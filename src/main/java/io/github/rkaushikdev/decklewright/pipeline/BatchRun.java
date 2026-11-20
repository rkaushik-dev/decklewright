package io.github.rkaushikdev.decklewright.pipeline;

import java.util.List;

/**
 * What a batch of runs did, and how many of them ended each way.
 *
 * @param runs one per accession that was run, in the order they ran; copied
 * @param delivered how many the policy decided to deliver
 * @param rejected how many it decided to reject
 * @param held how many it held for a person
 * @param failed how many stopped at a stage that failed
 * @param stoppedEarly true when the policy stopped the batch after a failure
 * @see Runner#runBatch(List, RunOptions)
 */
public record BatchRun(List<RunResult> runs, int delivered, int rejected, int held, int failed, boolean stoppedEarly) {

    /** A batch of the given parts; the runs are copied. */
    public BatchRun {
        runs = List.copyOf(runs);
    }
}
