package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Running stages over accessions.
 *
 * <p>The whole run is one accession. A batch is a loop over that, not a different code path, because
 * the last thing a lab needs is a batch that behaves differently from the single sheet somebody
 * tested the profile against.
 */
public final class Runner {

    /** What a run supplies itself, before any stage has run. */
    private static final List<String> STARTING = List.of("bytes", "path");

    private Runner() {}

    /**
     * Run every stage over one accession, then decide what to do with it.
     *
     * <p>It stops at the first failure. There is nothing useful a later stage can do with a job whose
     * crop could not be planned, and running them anyway produces a second, more confusing error that
     * people then try to fix first. A run that stops has no decision; one that does not is decided
     * from its grading and its warnings under the policy.
     *
     * @return what every stage reached did, and the decision or the error
     * @throws PipelineError before anything runs when the stages cannot be ordered or need something
     *     nothing produces and the run does not supply (the message is the first objection, the
     *     details carry the accession and how many objections there were), or when the accession is
     *     empty
     */
    public static RunResult runJob(JobInput input, RunOptions options) {
        long started = System.nanoTime();
        List<Stage> stages = StageGraph.order(options.stages());

        List<String> objections = StageGraph.objectionsToGraph(stages, STARTING);
        if (!objections.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("accession", input.accession());
            details.put("objections", objections.size());
            throw new PipelineError(objections.get(0), details);
        }

        StageContext context = new StageContext(
                input.profile(),
                input.sampling(),
                options.version(),
                options.host(),
                options.at().orElseGet(Instant::now),
                options.level(),
                options.capture(),
                options.crop());

        Map<String, Object> supplied = new LinkedHashMap<>();
        supplied.put("bytes", input.bytes());
        input.path().ifPresent(path -> supplied.put("path", path));
        Job job = Job.start(input.accession(), supplied);

        List<StageResult> results = new ArrayList<>(stages.size());
        Optional<RuntimeException> error = Optional.empty();
        for (Stage item : stages) {
            StageResult result = Stages.runStage(item, job, context);
            results.add(result);
            job = result.job();
            if (!result.ok()) {
                error = result.error();
                break;
            }
        }

        Policy rules = options.policy().orElseGet(() -> Policies.policyFor(input.profile()));
        Optional<Decision> decision = Optional.empty();
        if (error.isEmpty()) {
            DecisionInput finished = new DecisionInput(
                    job.get("quality", QcResult.class),
                    job.warnings(),
                    options.alreadyThere().orElse(false));
            decision = Optional.of(Policies.decide(finished, rules));
        }

        return new RunResult(input.accession(), job, results, error, decision, Stages.since(started));
    }

    /**
     * Run a whole shift: every accession in turn, until the policy says a failure stops the batch.
     *
     * @throws PipelineError as {@link #runJob(JobInput, RunOptions)} does, which stops the batch
     */
    public static BatchRun runBatch(List<JobInput> items, RunOptions options) {
        List<RunResult> runs = new ArrayList<>(items.size());
        int failed = 0;
        boolean stoppedEarly = false;

        for (JobInput item : items) {
            Policy rules = options.policy().orElseGet(() -> Policies.policyFor(item.profile()));
            if (!Policies.keepGoing(rules, failed)) {
                stoppedEarly = true;
                break;
            }
            RunResult out = runJob(item, options);
            runs.add(out);
            if (!out.ok()) {
                failed += 1;
            }
        }

        return new BatchRun(
                runs,
                counted(runs, Verdict.DELIVER),
                counted(runs, Verdict.REJECT),
                counted(runs, Verdict.HOLD),
                failed,
                stoppedEarly);
    }

    /**
     * What a run did, for a log or the terminal: a line with the decision and its reason, or saying
     * after how many stages it failed, then one indented line per stage.
     */
    public static List<String> describeRun(RunResult run) {
        String head = run.decision()
                .map(decided ->
                        run.accession() + ": " + decided.decision() + ", " + decided.reason() + ", " + run.ms() + " ms")
                .orElseGet(() ->
                        run.accession() + ": failed after " + run.results().size() + " stages, " + run.ms() + " ms");

        List<String> lines = new ArrayList<>(run.results().size() + 1);
        lines.add(head);
        for (StageResult result : run.results()) {
            lines.add("  " + Stages.describeResult(result));
        }
        return List.copyOf(lines);
    }

    /**
     * The plan for the standard run, before anything is read. A lab checking a new profile runs this
     * rather than a sheet.
     *
     * @see #describeRunPlan(List)
     */
    public static List<String> describeRunPlan() {
        return describeRunPlan(StandardStages.standardStages());
    }

    /**
     * The plan for a run of the given stages, before anything is read.
     *
     * @throws PipelineError when the stages cannot be ordered
     * @see StageGraph#describeGraph(List)
     */
    public static List<String> describeRunPlan(List<Stage> stages) {
        return StageGraph.describeGraph(stages);
    }

    private static int counted(List<RunResult> runs, Verdict verdict) {
        return (int) runs.stream()
                .filter(run -> run.decision()
                        .filter(decided -> decided.decision() == verdict)
                        .isPresent())
                .count();
    }
}
