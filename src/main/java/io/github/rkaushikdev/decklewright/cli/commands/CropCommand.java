package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.cli.ParsedArgs;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.derive.Derivatives;
import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.derive.DerivedSet;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.manifest.PayloadEntry;
import io.github.rkaushikdev.decklewright.plan.CropPlanner;
import io.github.rkaushikdev.decklewright.plan.Explanations;
import io.github.rkaushikdev.decklewright.plan.PlanOptions;
import io.github.rkaushikdev.decklewright.plan.PlannedCrop;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.store.AtomicFiles;
import io.github.rkaushikdev.decklewright.store.WrittenSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cut one sheet and write the derivatives.
 *
 * <p>This is the command for a curator with one plate and no workspace. It writes the image files
 * and nothing else: no sidecar, no ledger, no folder structure. A run that has to be traceable goes
 * through the batch command, and the difference is deliberate rather than a gap.
 *
 * <p>It walks the same steps the pipeline walks, in the same order, calling the same functions, so
 * the two cannot drift apart on how a sheet is cut.
 */
public final class CropCommand implements Command {

    private static final Map<String, OptionSpec> OPTIONS = Args.spec(
            Common.CAPTURE_OPTIONS,
            Map.entry(
                    "out",
                    OptionSpec.string("where to put the files").withLetter('o').withFallback(".")),
            Map.entry(
                    "name",
                    OptionSpec.string("the accession, if not the file name").withLetter('n')),
            Map.entry(
                    "only",
                    OptionSpec.string("write only this derivative, may repeat").repeated()),
            Map.entry(
                    "force",
                    OptionSpec.flag("replace files that are already there").withLetter('f')),
            Map.entry("dry-run", OptionSpec.flag("say what would be written and write nothing")));

    @Override
    public String usage() {
        return "deckle crop <capture> [options]";
    }

    @Override
    public String describe() {
        return "cut one sheet and write its derivatives";
    }

    @Override
    public Map<String, OptionSpec> options() {
        return OPTIONS;
    }

    /**
     * Read, plan and derive one capture, and write the files into the output directory, which is taken
     * relative to the io's working directory.
     *
     * @return two when no capture was named, one when it could not be cut, nothing was left to write,
     *     or a file was in the way, otherwise zero
     */
    @Override
    public int run(ParsedArgs args, Io sink) {
        Optional<String> path = args.positionalAt(0);
        if (path.isEmpty()) {
            sink.err("which capture? try: deckle crop g4181.png --out delivered");
            return 2;
        }

        try {
            Profile profile = Common.profileNamed(args.string("profile").orElseThrow());
            CaptureFile capture = Common.captureAt(path.get());
            String accession = args.string("name").orElse(capture.accession());
            Surface surface = Codecs.readImage(capture.bytes(), path.get());

            CaptureReading reading = Captures.readCapture(surface);
            PlannedCrop planned = CropPlanner.planCrop(
                    reading,
                    profile,
                    surface.bounds(),
                    Common.samplingFrom(args.number("ppi"), profile),
                    PlanOptions.defaults());
            DerivedSet derived = Derivatives.deriveFromPlan(
                    surface, planned.plan(), profile, accession, Settings.derivativeRecipes(profile));

            Set<String> wanted = Set.copyOf(args.strings("only"));
            List<DerivedFile> files = wanted.isEmpty()
                    ? derived.files()
                    : derived.files().stream()
                            .filter(one -> wanted.contains(one.name()))
                            .toList();

            if (files.isEmpty()) {
                sink.err("nothing to write, this profile makes "
                        + derived.files().stream().map(DerivedFile::name).collect(Collectors.joining(", ")));
                return 1;
            }

            sink.out(accession + ": " + Explanations.summarise(planned.plan()));
            for (DerivedFile file : files) {
                sink.out("  " + file.fileName() + ", " + Common.humanBytes(file.bytes().length) + ", "
                        + file.sampling());
            }
            if (!planned.settled()) {
                sink.err("warning: the crop rules did not settle");
            }
            for (String warning : planned.plan().warnings()) {
                sink.err("warning: " + warning);
            }

            String out = args.string("out").orElseThrow();
            if (args.flag("dry-run")) {
                sink.out("would write " + files.size() + " files into " + out);
                return 0;
            }

            WrittenSet written = AtomicFiles.writeSetAtomic(
                    sink.cwd().resolve(out),
                    files.stream()
                            .map(file -> new PayloadEntry(file.fileName(), file.bytes()))
                            .toList(),
                    args.flag("force"));
            sink.out("wrote " + written.written().size() + " files, " + Common.humanBytes(written.bytes()));
            return 0;
        } catch (DeckleError caught) {
            return Common.report(sink, caught);
        }
    }
}
