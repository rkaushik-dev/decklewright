package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.PipelineError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Making stages, running one against a job, and editing a list of them.
 *
 * <p>A stage's work returns the values it produced, not a job, and running it here is what puts
 * them on the job and records that it ran. A list of stages is edited by name, which is how a lab
 * adds its own step or drops one without touching the standard list.
 */
public final class Stages {

    private Stages() {}

    /**
     * A required stage that needs nothing and produces nothing.
     *
     * @throws PipelineError when the name is null or empty, or there is no action
     */
    public static Stage stage(String name, StageAction action) {
        return stage(name, List.of(), List.of(), action);
    }

    /**
     * A required stage; {@link Stage#asOptional()} makes one that is skipped when a job cannot feed it.
     *
     * @param needs the values it wants off the job
     * @param produces the values it promises to put back
     * @throws PipelineError when the name is null or empty, or there is no action
     */
    public static Stage stage(String name, List<String> needs, List<String> produces, StageAction action) {
        return new Stage(name, needs, produces, false, action);
    }

    /**
     * Run one stage against a job.
     *
     * <p>A stage that says it produces something and does not is a failure here rather than three
     * stages later, because the stage that was going to need it has a much worse error message. A
     * value it produced that the job already carries is a failure too.
     *
     * <p>Anything the stage throws is caught and becomes the result, not just this tree's errors: one
     * accession's failure must not end the run. An error of this tree is annotated with the stage and
     * the accession on the way.
     *
     * @param context what every stage gets, such as the profile
     * @return a skip when an optional stage is reached without what it needs, a failure when a
     *     required one is or when the stage throws, and otherwise the new job
     */
    public static StageResult runStage(Stage item, Job job, StageContext context) {
        long started = System.nanoTime();
        List<String> missing = Job.missingFrom(job, item.needs());

        if (!missing.isEmpty()) {
            if (item.optional()) {
                Job noted = job.noted(item.name() + " was skipped, no " + String.join(" or ", missing));
                return new StageResult.Skipped(item.name(), noted, missing, since(started));
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("stage", item.name());
            details.put("accession", job.accession());
            details.put("wanted", String.join(", ", missing));
            PipelineError error = new PipelineError("a stage was reached without what it needs", details);
            return new StageResult.Failed(item.name(), job, missing, error, since(started));
        }

        try {
            Map<String, ?> produced = Objects.requireNonNullElse(item.run(job, context), Map.of());
            List<String> unmade = item.produces().stream()
                    .filter(name -> produced.get(name) == null)
                    .toList();
            if (!unmade.isEmpty()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("stage", item.name());
                details.put("accession", job.accession());
                details.put("missing", String.join(", ", unmade));
                throw new PipelineError("a stage did not produce what it said it would", details);
            }
            return new StageResult.Ran(item.name(), job.with(produced).finished(item.name()), since(started));
        } catch (DeckleError caught) {
            Map<String, Object> where = new LinkedHashMap<>();
            where.put("stage", item.name());
            where.put("accession", job.accession());
            return new StageResult.Failed(item.name(), job, List.of(), caught.annotate(where), since(started));
        } catch (RuntimeException caught) {
            return new StageResult.Failed(item.name(), job, List.of(), caught, since(started));
        }
    }

    /**
     * Refuse a list in which two stages share a name.
     *
     * @throws PipelineError naming the first name used twice
     */
    public static void requireDistinct(List<Stage> stages) {
        Set<String> seen = new HashSet<>();
        for (Stage item : stages) {
            if (!seen.add(item.name())) {
                throw new PipelineError("two stages have the same name", Map.of("name", item.name()));
            }
        }
    }

    /** The stages without the ones named, in their order; a name that is not there is ignored. */
    public static List<Stage> without(List<Stage> stages, List<String> names) {
        Set<String> drop = Set.copyOf(names);
        return stages.stream().filter(item -> !drop.contains(item.name())).toList();
    }

    /**
     * Put a stage in front of another one by name, which is how a lab adds its own step without
     * editing the standard list.
     *
     * @throws PipelineError when no stage has that name; the details name it
     */
    public static List<Stage> insertBefore(List<Stage> stages, String before, Stage item) {
        int at = IntStream.range(0, stages.size())
                .filter(i -> stages.get(i).name().equals(before))
                .findFirst()
                .orElseThrow(() -> new PipelineError("there is no stage by that name", Map.of("name", before)));
        List<Stage> out = new ArrayList<>(stages.size() + 1);
        out.addAll(stages.subList(0, at));
        out.add(item);
        out.addAll(stages.subList(at, stages.size()));
        return List.copyOf(out);
    }

    /**
     * One line for a log, such as {@code read ran in 12 ms}, {@code grade was skipped, no target} or
     * {@code plan failed: } and the error's message.
     */
    public static String describeResult(StageResult result) {
        return switch (result) {
            case StageResult.Ran ran -> ran.stage() + " ran in " + ran.ms() + " ms";
            case StageResult.Skipped skipped ->
                skipped.stage() + " was skipped, no " + String.join(" or ", skipped.missing());
            case StageResult.Failed failed ->
                failed.stage() + " failed: "
                        + Objects.requireNonNullElse(failed.failure().getMessage(), "for no stated reason");
        };
    }

    /** The whole milliseconds since a reading of the monotonic clock. */
    static long since(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
