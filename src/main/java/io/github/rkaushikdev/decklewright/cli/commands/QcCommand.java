package io.github.rkaushikdev.decklewright.cli.commands;

import io.github.rkaushikdev.decklewright.DeckleError;
import io.github.rkaushikdev.decklewright.cli.Args;
import io.github.rkaushikdev.decklewright.cli.OptionSpec;
import io.github.rkaushikdev.decklewright.cli.ParsedArgs;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlanner;
import io.github.rkaushikdev.decklewright.plan.PlanOptions;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.qc.Grading;
import io.github.rkaushikdev.decklewright.qc.GradingOptions;
import io.github.rkaushikdev.decklewright.qc.QcInput;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Json;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Grade a capture and say what to change on the stand.
 *
 * <p>The exit code is the point. An operator runs this over a shift's captures from a shell loop and
 * wants a non zero code for the ones to look at, so the code is zero when the capture reached the
 * level the profile asks for and one when it did not, whatever else was printed.
 */
public final class QcCommand implements Command {

    /** The top of the star ladder a level can be asked for at. */
    private static final double TOP_STARS = 4;

    private static final Map<String, OptionSpec> OPTIONS = Args.spec(
            Common.CAPTURE_OPTIONS,
            Map.entry("json", OptionSpec.flag("write the grading as json").withLetter('j')),
            Map.entry("stars", OptionSpec.number("the level to measure against").withLetter('s')),
            Map.entry("crop", OptionSpec.flag("grade the crop rather than the whole capture")));

    @Override
    public String usage() {
        return "deckle qc <capture> [options]";
    }

    @Override
    public String describe() {
        return "grade a capture against the profile and say what to fix";
    }

    @Override
    public Map<String, OptionSpec> options() {
        return OPTIONS;
    }

    /**
     * Grade one capture, against the planned crop when asked, and print the grading as a report or
     * as json.
     *
     * @return two when no capture was named, one when it could not be graded or did not reach the
     *     level, otherwise zero
     */
    @Override
    public int run(ParsedArgs args, Io sink) {
        Optional<String> path = args.positionalAt(0);
        if (path.isEmpty()) {
            sink.err("which capture? try: deckle qc g4181.png");
            return 2;
        }

        try {
            Profile base = Common.profileNamed(args.string("profile").orElseThrow());
            OptionalDouble stars = args.number("stars");
            Profile profile = stars.isPresent() ? atLevel(base, stars.getAsDouble()) : base;

            CaptureFile capture = Common.captureAt(path.get());
            Surface surface = Codecs.readImage(capture.bytes(), path.get());
            CaptureReading reading = Captures.readCapture(surface);
            Optional<Sampling> sampling = Common.samplingFrom(args.number("ppi"), profile);

            QcInput input = QcInput.of(surface, reading, profile);
            if (sampling.isPresent()) {
                input = input.withSampling(sampling.get());
            }
            if (args.flag("crop")) {
                input = input.withCrop(
                        CropPlanner.planCrop(reading, profile, surface.bounds(), sampling, PlanOptions.defaults())
                                .plan()
                                .rect());
            }

            GradingOptions grading = GradingOptions.defaults().withItem(capture.accession());
            if (args.flag("json")) {
                Map<String, Object> graded = Grading.gradingToJson(input, grading);
                sink.out(Json.stringify(graded, 2));
                return Boolean.TRUE.equals(graded.get("passed")) ? 0 : 1;
            }
            for (String line : Grading.reportOn(input, grading)) {
                sink.out(line);
            }
            return Grading.gradeCapture(input, grading.qc()).result().passed() ? 0 : 1;
        } catch (DeckleError caught) {
            return Common.report(sink, caught);
        }
    }

    /**
     * The profile asking for another level, everything else the same.
     *
     * @throws io.github.rkaushikdev.decklewright.BadArgumentError when the level is not a whole
     *     number from zero to four
     */
    private static Profile atLevel(Profile base, double stars) {
        Guards.requireInteger(stars, "stars");
        Guards.requireInRange(stars, 0, TOP_STARS, "stars");
        Profile.Qc qc = base.qc();
        Profile.Qc wanted = new Profile.Qc(
                qc.samplingPpi(),
                qc.samplingTolerance(),
                qc.skewTolerance(),
                qc.illuminationTolerance(),
                qc.grayBalanceTolerance(),
                qc.minimumSnr(),
                qc.maximumClipping(),
                (int) stars);
        return new Profile(
                base.name(),
                base.title(),
                base.version(),
                base.parent(),
                base.capture(),
                base.crop(),
                base.derivatives(),
                wanted,
                base.naming());
    }
}
