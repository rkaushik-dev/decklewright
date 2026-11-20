package io.github.rkaushikdev.decklewright.pipeline;

import java.util.Map;

/**
 * The work a stage does.
 *
 * <p>It is handed the job and the run's context and gives back the values it produced, not a job. It
 * never sees the job's history and cannot edit it, and it does not have to remember to record that
 * it ran: {@link Stages#runStage} does that.
 */
@FunctionalInterface
public interface StageAction {

    /**
     * Do the stage's work.
     *
     * @param job what is known about the accession so far
     * @param context what every stage of the run gets, such as the profile
     * @return the values produced, by name; null or an empty map when nothing was
     * @throws RuntimeException for anything that stops the stage, which becomes a failure of this
     *     accession and not of the run
     */
    Map<String, ?> run(Job job, StageContext context);
}
