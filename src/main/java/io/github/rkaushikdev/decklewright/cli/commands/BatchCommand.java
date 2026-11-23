package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.cli.ParsedArgs;
import io.github.rkaushikdev.decklewright.manifest.DeliveryOptions;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.pipeline.Policies;
import io.github.rkaushikdev.decklewright.pipeline.Policy;
import io.github.rkaushikdev.decklewright.pipeline.Processed;
import io.github.rkaushikdev.decklewright.pipeline.ProcessedIncoming;
import io.github.rkaushikdev.decklewright.pipeline.RunOptions;
import io.github.rkaushikdev.decklewright.pipeline.Runner;
import io.github.rkaushikdev.decklewright.pipeline.WorkspaceRunner;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.store.Dispatch;
import io.github.rkaushikdev.decklewright.store.IncomingCapture;
import io.github.rkaushikdev.decklewright.store.Ledger;
import io.github.rkaushikdev.decklewright.store.LedgerEntry;
import io.github.rkaushikdev.decklewright.store.LedgerFilter;
import io.github.rkaushikdev.decklewright.store.LedgerRead;
import io.github.rkaushikdev.decklewright.store.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Work a whole shift out of a workspace.
 *
 * <p>This is the command a lab actually runs. Everything waiting in the incoming folder is read,
 * cut, graded, packed and written, and every one of them ends up either in a folder with a sidecar
 * beside it or as a line in the ledger saying why not.
 *
 * <p>It does not stop on a bad sheet unless it is told to. Two hundred captures come off a stand
 * overnight and one of them is always a lens cap.
 */
public final class BatchCommand implements Command {

    private static final Map<String, OptionSpec> OPTIONS = Args.spec(
            Common.CAPTURE_OPTIONS,
            Map.entry("stars", OptionSpec.number("the level to deliver at").withLetter('s')),
            Map.entry(
                    "force",
                    OptionSpec.flag("replace a delivery that is already there").withLetter('f')),
            Map.entry("stop", OptionSpec.flag("stop the whole shift at the first failure")),
            Map.entry(
                    "quiet",
                    OptionSpec.flag("one line per accession rather than one per stage")
                            .withLetter('q')),
            Map.entry(
                    "version",
                    OptionSpec.string("what to record as the agent").withFallback(DeliveryOptions.UNKNOWN_VERSION)),
            Map.entry("host", OptionSpec.string("which stand this is, for the provenance")),
            Map.entry("report", OptionSpec.flag("summarise the ledger by outcome and level and work nothing")),
            Map.entry("since", OptionSpec.string("with --report, the first day or time to count, inclusive")),
            Map.entry("until", OptionSpec.string("with --report, the day or time to stop counting before")));

    @Override
    public String usage() {
        return "deckle batch <workspace> [options]";
    }

    @Override
    public String describe() {
        return "work everything waiting in a workspace";
    }

    @Override
    public Map<String, OptionSpec> options() {
        return OPTIONS;
    }

    /**
     * Work everything waiting, print what happened to each and the shift's totals off the ledger, or
     * with {@code --report} only summarise the ledger over a period.
     *
     * @return two when no workspace was named, one when it could not be worked, any accession
     *     failed or was skipped, or a report found ledger lines it could not read, otherwise zero
     */
    @Override
    public int run(ParsedArgs args, Io sink) {
        Optional<String> root = args.positionalAt(0);
        if (root.isEmpty()) {
            sink.err("which workspace? try: deckle batch /lab/oversize");
            return 2;
        }

        try {
            if (args.flag("report")) {
                return report(Workspace.at(Path.of(root.get())), args, sink);
            }
            Profile profile = Common.profileNamed(args.string("profile").orElseThrow());
            Workspace workspace = Workspace.at(Path.of(root.get()));
            Optional<Sampling> sampling = Common.samplingFrom(args.number("ppi"), profile);

            Policy rules = Policies.policyFor(profile);
            OptionalDouble stars = args.number("stars");
            if (stars.isPresent()) {
                rules = rules.withMinimumStars(stars.getAsDouble());
            }
            rules = rules.withOverwrite(args.flag("force")).withStopOnFailure(args.flag("stop"));

            List<IncomingCapture> waiting = workspace.incoming();
            if (waiting.isEmpty()) {
                sink.out("nothing is waiting");
                return 0;
            }
            sink.out(waiting.size() + " waiting, profile " + profile.name());

            RunOptions options = RunOptions.defaults()
                    .withPolicy(rules)
                    .withVersion(args.string("version").orElseThrow())
                    .withAt(sink.now());
            Optional<String> host = args.string("host");
            if (host.isPresent()) {
                options = options.withHost(host.get());
            }
            ProcessedIncoming out = WorkspaceRunner.processIncoming(workspace, profile, sampling, options);

            for (Processed one : out.done()) {
                if (args.flag("quiet")) {
                    LedgerEntry entry = one.entry();
                    sink.out(entry.accession() + ": " + entry.outcome() + ", "
                            + entry.reason().orElse("ok"));
                } else {
                    Runner.describeRun(one.run()).forEach(sink::out);
                }
            }

            if (out.stoppedEarly()) {
                sink.err("stopped after " + out.done().size() + " of " + waiting.size() + ", the policy says to");
            }

            sink.out("");
            Ledger.describeSummary(Dispatch.shiftReport(workspace).summary()).forEach(sink::out);

            int bad = out.counts().getOrDefault("failed", 0) + out.counts().getOrDefault("skipped", 0);
            if (bad > 0) {
                sink.err(bad + " accessions need somebody to look at them");
                return 1;
            }
            return 0;
        } catch (DeckleError caught) {
            return Common.report(sink, caught);
        }
    }

    /**
     * The ledger's entries over the period asked for, summarised for a progress report. Nothing in
     * the workspace is touched.
     *
     * @return one when a ledger line could not be read, so the counts are short, otherwise zero
     */
    private static int report(Workspace workspace, ParsedArgs args, Io sink) {
        LedgerRead read = Ledger.readLedger(workspace.paths().ledger());
        LedgerFilter filter = LedgerFilter.everything();
        Optional<String> since = args.string("since");
        Optional<String> until = args.string("until");
        if (since.isPresent()) {
            filter = filter.withSince(since.get());
        }
        if (until.isPresent()) {
            filter = filter.withUntil(until.get());
        }
        String period = since.map(from -> " from " + from).orElse("")
                + until.map(to -> " until " + to).orElse("");
        sink.out("ledger" + (period.isEmpty() ? ", every entry" : period));
        Ledger.describeSummary(Ledger.summarise(Ledger.select(read.entries(), filter)))
                .forEach(sink::out);
        if (read.unreadable() > 0) {
            sink.err(read.unreadable() + " ledger lines could not be read and are not counted");
            return 1;
        }
        return 0;
    }
}
