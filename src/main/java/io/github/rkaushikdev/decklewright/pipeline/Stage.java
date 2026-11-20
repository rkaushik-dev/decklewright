package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.PipelineError;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One step, and what it needs before it can take one.
 *
 * <p>A stage says what it wants off the job and what it will put back. Those two lists are the
 * whole of the wiring: the graph is worked out from them rather than written down, so a stage
 * inserted in the middle cannot be inserted in the wrong place.
 *
 * @param name what the stage is called in the graph and the log, unique within a run
 * @param needs the values it wants off the job; copied
 * @param produces the values it promises to put back; copied
 * @param optional true when a job that cannot feed it skips it rather than failing
 * @param action the work it does
 * @see Stages#stage(String, List, List, StageAction)
 */
public record Stage(String name, List<String> needs, List<String> produces, boolean optional, StageAction action) {

    /**
     * A stage of the given parts; the lists are copied.
     *
     * @throws PipelineError when the name is null or empty, or there is no action; the second names
     *     the stage
     */
    public Stage {
        if (name == null || name.isEmpty()) {
            throw new PipelineError("a stage has to have a name", Map.of());
        }
        if (action == null) {
            throw new PipelineError("a stage has to do something", Map.of("name", name));
        }
        needs = List.copyOf(Objects.requireNonNull(needs, "needs"));
        produces = List.copyOf(Objects.requireNonNull(produces, "produces"));
    }

    /** This stage, skipped rather than failed when a job does not carry what it needs. */
    public Stage asOptional() {
        return new Stage(name, needs, produces, true, action);
    }

    /**
     * Do this stage's work, without any of the checks and bookkeeping {@link Stages#runStage} adds.
     *
     * @return the values produced, by name; null or an empty map when nothing was
     */
    public Map<String, ?> run(Job job, StageContext context) {
        return action.run(job, context);
    }
}
