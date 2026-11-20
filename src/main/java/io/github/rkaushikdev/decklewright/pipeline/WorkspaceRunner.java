package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.manifest.Payload;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.store.AtomicFiles;
import io.github.rkaushikdev.decklewright.store.DeliverOptions;
import io.github.rkaushikdev.decklewright.store.Dispatch;
import io.github.rkaushikdev.decklewright.store.Dispatched;
import io.github.rkaushikdev.decklewright.store.FailureOptions;
import io.github.rkaushikdev.decklewright.store.IncomingCapture;
import io.github.rkaushikdev.decklewright.store.LedgerEntry;
import io.github.rkaushikdev.decklewright.store.Workspace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The pipeline as the outside sees it.
 *
 * <p>One call takes a workspace and an accession and does everything: read, cut, grade, pack, write,
 * and record. It is the only code in the tree that both computes and writes, and it is deliberately
 * short, because everything it depends on has already been tested without a disk.
 */
public final class WorkspaceRunner {

    private WorkspaceRunner() {}

    /**
     * Work one capture and put the result where the policy says it goes.
     *
     * <p>A failure is recorded as carefully as a success. A shift where forty sheets went out and one
     * threw is a shift with one sheet nobody has looked at, and the only way that sheet gets looked at
     * is if it is in the ledger. So a run that fails is recorded as {@code failed} with the error's
     * message, one that is held is recorded as {@code skipped} with the reason, and nothing is written
     * for either. Anything else is delivered to the folder the decision names, under the policy's
     * overwrite rule, with the grading and the job's warnings on its ledger line.
     *
     * <p>Whether the accession is already there is asked of the workspace unless the options say.
     *
     * @return the run, where its delivery went, and the ledger line
     * @throws io.github.rkaushikdev.decklewright.PipelineError when the stages cannot run at all, as
     *     {@link Runner#runJob(JobInput, RunOptions)} does; nothing is recorded then
     * @throws io.github.rkaushikdev.decklewright.StoreError when the delivery or the ledger line could
     *     not be written
     */
    public static Processed process(Workspace workspace, JobInput input, RunOptions options) {
        Policy rules = options.policy().orElseGet(() -> Policies.policyFor(input.profile()));
        boolean alreadyThere = options.alreadyThere().orElseGet(() -> workspace.has(input.accession()));
        RunResult run = Runner.runJob(input, options.withPolicy(rules).withAlreadyThere(alreadyThere));
        String profile = input.profile().name();

        if (!run.ok()) {
            String reason = run.error().map(RuntimeException::getMessage).orElse("the run stopped without saying why");
            FailureOptions failure = new FailureOptions("failed", Optional.of(profile), options.at());
            LedgerEntry entry = Dispatch.recordFailure(workspace, input.accession(), reason, failure);
            return new Processed(run, Optional.empty(), entry);
        }

        // A run that did not fail always carries a decision.
        Decision decision = run.decision().orElseThrow();
        if (decision.decision() == Verdict.HOLD) {
            FailureOptions held = new FailureOptions("skipped", Optional.of(profile), options.at());
            LedgerEntry entry = Dispatch.recordFailure(workspace, input.accession(), decision.reason(), held);
            return new Processed(run, Optional.empty(), entry);
        }

        DeliverOptions delivery = new DeliverOptions(
                decision.folder(),
                rules.overwrite(),
                Optional.of(profile),
                starsOf(run.job()),
                run.job().warnings(),
                options.at());
        Dispatched written = Dispatch.deliver(workspace, run.job().need("payload", Payload.class), delivery);
        return new Processed(run, Optional.of(written.directory()), written.entry());
    }

    /**
     * Work everything waiting in the incoming folder, in name order, against one profile.
     *
     * <p>The policy decides whether one bad sheet stops the shift. A lab that has set that expects it
     * to hold here as much as in a batch it assembled itself; ignoring it would mean a stand left
     * running overnight works two hundred captures against a scanner that failed on the first one.
     * Files the codecs do not recognise by name, like the spreadsheet every incoming folder has in it,
     * are left alone.
     *
     * @param sampling a frequency the caller measured, used for every capture over the one its
     *     reading carries
     * @return what was done with each capture, how many ended each way, and whether the policy stopped
     *     the shift
     * @throws io.github.rkaushikdev.decklewright.StoreError when the incoming folder could not be
     *     listed or a capture could not be read
     */
    public static ProcessedIncoming processIncoming(
            Workspace workspace, Profile profile, Optional<Sampling> sampling, RunOptions options) {
        List<IncomingCapture> waiting = workspace.incoming();
        Policy rules = options.policy().orElseGet(() -> Policies.policyFor(profile));

        List<Processed> done = new ArrayList<>(waiting.size());
        Map<String, Integer> counts = new LinkedHashMap<>();
        int failed = 0;
        boolean stoppedEarly = false;

        for (IncomingCapture item : waiting) {
            if (!Policies.keepGoing(rules, failed)) {
                stoppedEarly = true;
                break;
            }
            JobInput input = new JobInput(
                    item.accession(),
                    AtomicFiles.readBytes(item.path()),
                    profile,
                    sampling,
                    Optional.of(item.path().toString()));
            Processed out = process(workspace, input, options.withPolicy(rules));
            done.add(out);
            counts.merge(out.entry().outcome(), 1, Integer::sum);
            if (out.entry().outcome().equals("failed")) {
                failed += 1;
            }
        }
        return new ProcessedIncoming(done, counts, stoppedEarly);
    }

    private static OptionalInt starsOf(Job job) {
        return job.get("quality", QcResult.class)
                .map(quality -> OptionalInt.of(quality.stars()))
                .orElseGet(OptionalInt::empty);
    }
}
