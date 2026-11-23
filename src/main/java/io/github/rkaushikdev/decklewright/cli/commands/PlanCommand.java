package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.cli.ParsedArgs;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.plan.CropPlanner;
import io.github.rkaushikdev.decklewright.plan.Explanations;
import io.github.rkaushikdev.decklewright.plan.PlanOptions;
import io.github.rkaushikdev.decklewright.plan.PlannedCrop;
import io.github.rkaushikdev.decklewright.plan.RuleSets;
import io.github.rkaushikdev.decklewright.plan.Tension;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Say where the crop would be, and write nothing.
 *
 * <p>This is the command a lab runs when it is arguing about a profile. It does every step a real
 * run does up to the point where bytes would be written, so what it reports is what would happen and
 * not an estimate of it.
 */
public final class PlanCommand implements Command {

    private static final Map<String, OptionSpec> OPTIONS = Args.spec(
            Common.CAPTURE_OPTIONS,
            Map.entry(
                    "unit",
                    OptionSpec.string("the unit to report lengths in")
                            .withLetter('u')
                            .withFallback("in")),
            Map.entry(
                    "json",
                    OptionSpec.flag("write the plan as json instead of prose").withLetter('j')),
            Map.entry("rules", OptionSpec.flag("list every rule that was tried, not only the ones that moved it")));

    @Override
    public String usage() {
        return "deckle plan <capture> [options]";
    }

    @Override
    public String describe() {
        return "work out where the crop would be, without writing anything";
    }

    @Override
    public Map<String, OptionSpec> options() {
        return OPTIONS;
    }

    /**
     * Plan the crop of one capture and print it as prose or as json.
     *
     * @return two when no capture was named, one when it could not be planned or the rules did not
     *     settle, otherwise zero
     */
    @Override
    public int run(ParsedArgs args, Io sink) {
        Optional<String> path = args.positionalAt(0);
        if (path.isEmpty()) {
            sink.err("which capture? try: deckle plan g4181.png");
            return 2;
        }

        try {
            Profile profile = Common.profileNamed(args.string("profile").orElseThrow());
            ReadCapture read = Common.readCaptureAt(path.get(), profile);
            CaptureFile capture = read.file();
            Surface surface = read.surface();
            CaptureReading reading = read.reading();
            Optional<Sampling> sampling = Common.samplingFrom(args.number("ppi"), profile);

            PlannedCrop out =
                    CropPlanner.planCrop(reading, profile, surface.bounds(), sampling, PlanOptions.defaults());

            if (args.flag("json")) {
                sink.out(Json.stringify(planToJson(capture.accession(), profile, out), 2));
                return 0;
            }

            sink.out(capture.accession() + ", profile " + profile.name());
            for (String line : Captures.summarise(reading)) {
                sink.out("  " + line);
            }
            sink.out("");
            Unit unit = Unit.parse(args.string("unit").orElseThrow());
            for (String line : explain(out.plan(), args.flag("rules") ? out.rules() : List.of(), unit)) {
                sink.out(line);
            }

            if (!out.settled()) {
                sink.err("the rules did not settle after " + out.passes() + " passes");
                return 1;
            }
            for (String warning : out.plan().warnings()) {
                sink.err("warning: " + warning);
            }
            return 0;
        } catch (DeckleError caught) {
            return Common.report(sink, caught);
        }
    }

    private static Map<String, Object> planToJson(String accession, Profile profile, PlannedCrop out) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("accession", accession);
        json.put("profile", profile.name());
        json.put("settled", out.settled());
        json.put("passes", (double) out.passes());
        json.put("rules", new ArrayList<Object>(out.rules()));
        json.put("rect", out.plan().rect().toJson());
        json.put(
                "deskewDegrees",
                Double.parseDouble(NumberText.fixed(out.plan().deskew().degrees(), 4)));
        json.put("ppi", out.plan().sampling().toJson());
        json.put("warnings", new ArrayList<Object>(out.plan().warnings()));
        return json;
    }

    /**
     * The plan explained, then, when rules were named, the ones among them that changed nothing and a
     * note for each pair of them that is known to argue.
     */
    private static List<String> explain(CropPlan plan, List<String> tried, Unit unit) {
        List<String> lines = new ArrayList<>(Explanations.explain(plan, unit));
        List<String> moved = plan.trail();
        List<String> quiet =
                tried.stream().filter(name -> !moved.contains(name)).toList();
        if (!quiet.isEmpty()) {
            lines.add("rules that changed nothing: " + String.join(", ", quiet));
        }
        for (Tension tension : RuleSets.KNOWN_TENSIONS) {
            if (tried.containsAll(tension.between())) {
                lines.add("note: " + tension.says());
            }
        }
        return lines;
    }
}
