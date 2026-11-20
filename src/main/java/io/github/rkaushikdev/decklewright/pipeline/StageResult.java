package io.github.rkaushikdev.decklewright.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What came of running one stage against a job.
 *
 * <p>There are three outcomes and they are kept apart on purpose. It {@linkplain Ran ran} and
 * worked. It was {@linkplain Skipped skipped} because the job did not carry what it needs, which for
 * an optional stage is a normal Tuesday. Or it {@linkplain Failed failed}, which belongs to this
 * accession and not to the run.
 *
 * @see Stages#runStage(Stage, Job, StageContext)
 */
public sealed interface StageResult permits StageResult.Ran, StageResult.Skipped, StageResult.Failed {

    /** The stage's name. */
    String stage();

    /** What to carry on with: the new job when it ran, the job as it was otherwise. */
    Job job();

    /** Whether the stage's work was done. */
    boolean ran();

    /** Whether the run can carry on: true when it ran or an optional stage was skipped. */
    boolean ok();

    /** What it wanted off the job and did not get; empty when it had everything. */
    List<String> missing();

    /** Why it failed; empty when it did not. */
    Optional<RuntimeException> error();

    /** How long it took, in whole milliseconds. */
    long ms();

    /**
     * The stage ran and its values are on the job.
     *
     * @param stage the stage's name
     * @param job the job with what the stage produced, and the stage recorded as done
     * @param ms how long it took, in whole milliseconds
     */
    record Ran(String stage, Job job, long ms) implements StageResult {

        /** A result of the given parts. */
        public Ran {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(job, "job");
        }

        @Override
        public boolean ran() {
            return true;
        }

        @Override
        public boolean ok() {
            return true;
        }

        @Override
        public List<String> missing() {
            return List.of();
        }

        @Override
        public Optional<RuntimeException> error() {
            return Optional.empty();
        }
    }

    /**
     * An optional stage was passed over because the job did not carry what it needs.
     *
     * @param stage the stage's name
     * @param job the job with a note saying the stage was skipped
     * @param missing what it wanted and did not get; copied
     * @param ms how long it took, in whole milliseconds
     */
    record Skipped(String stage, Job job, List<String> missing, long ms) implements StageResult {

        /** A result of the given parts; the missing names are copied. */
        public Skipped {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(job, "job");
            missing = List.copyOf(missing);
        }

        @Override
        public boolean ran() {
            return false;
        }

        @Override
        public boolean ok() {
            return true;
        }

        @Override
        public Optional<RuntimeException> error() {
            return Optional.empty();
        }
    }

    /**
     * The stage could not be run, or it threw.
     *
     * @param stage the stage's name
     * @param job the job as it was before the stage
     * @param missing what a required stage wanted and did not get; empty when it threw
     * @param failure what went wrong; an error of this tree carries the stage and the accession in its
     *     details
     * @param ms how long it took, in whole milliseconds
     */
    record Failed(String stage, Job job, List<String> missing, RuntimeException failure, long ms)
            implements StageResult {

        /** A result of the given parts; the missing names are copied. */
        public Failed {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(job, "job");
            missing = List.copyOf(missing);
            Objects.requireNonNull(failure, "failure");
        }

        @Override
        public boolean ran() {
            return false;
        }

        @Override
        public boolean ok() {
            return false;
        }

        @Override
        public Optional<RuntimeException> error() {
            return Optional.of(failure);
        }
    }
}
