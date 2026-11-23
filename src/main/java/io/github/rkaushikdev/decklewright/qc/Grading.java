package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.qc.checks.DarkCorner;
import io.github.rkaushikdev.decklewright.qc.checks.GrayBalance;
import io.github.rkaushikdev.decklewright.qc.checks.Illumination;
import io.github.rkaushikdev.decklewright.qc.checks.Noise;
import io.github.rkaushikdev.decklewright.qc.checks.Sharpness;
import io.github.rkaushikdev.decklewright.qc.checks.StripOptions;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Grading a capture as the rest of the tree sees it: one call grades it and says what to do, and
 * one call each writes the grading out for a person or for a machine.
 *
 * <p>Nothing here writes a file or decides what happens next. A capture that grades at two stars is
 * still a capture, and whether two stars is good enough to deliver is the pipeline's decision, not
 * this layer's.
 */
public final class Grading {
    private Grading() {}

    /**
     * Grade a capture with the standard checks and advise on it.
     *
     * @see #gradeCapture(QcInput, QcOptions)
     */
    public static GradedCapture gradeCapture(QcInput input) {
        return gradeCapture(input, QcOptions.defaults());
    }

    /**
     * Grade a capture and say what to do about it in the same call.
     *
     * <p>The advice is the part an operator acts on and it is worth more than the level, because a
     * capture that failed on illumination and a capture that failed on focus both read two stars and
     * want opposite things done to the stand. Where a check can say which way the fault goes, it
     * does: a short illumination check adds which corner of the bed is darkest, read off the bed the
     * check measured with the sheet and the target kept off it, and a short gray
     * balance check adds which way the cast goes when there is a target to read it off. A short noise
     * check names the noisiest channel of a colour capture, a short sampling check gives both axes
     * when they differ, and a short channel registration check names the edge with the widest
     * fringe. The details follow the remedies, furthest short first.
     *
     * @param options the same options {@link QcRunner#runQc(QcInput, QcOptions)} takes
     */
    public static GradedCapture gradeCapture(QcInput input, QcOptions options) {
        QcResult out = QcRunner.runQc(input, options);
        List<String> advice = new ArrayList<>(Reports.remedies(out.findings(), out.wanted()));
        QcContext context = QcRunner.contextFor(input);
        for (Shortfall shortfall : Levels.shortfall(out.findings(), out.wanted())) {
            Detail detail = DETAILS.get(shortfall.name());
            if (detail != null) {
                detail.of(input, context).ifPresent(advice::add);
            }
        }
        return new GradedCapture(out, advice);
    }

    /** Which way a short check's fault goes, when the check can say. */
    @FunctionalInterface
    private interface Detail {
        Optional<String> of(QcInput input, QcContext context);
    }

    /** The edges in the order {@link Sharpness#edgeStrips} gives their strips. */
    private static final List<String> EDGES = List.of("top", "bottom", "left", "right");

    /** What each check that can say which way its fault goes adds to its remedy. */
    private static final Map<String, Detail> DETAILS = Map.of(
            "illumination", Grading::darkSide,
            "grayBalance", Grading::castWay,
            "noise", Grading::noisyChannel,
            "sampling", Grading::bothAxes,
            "samplingFloor", Grading::bothAxes,
            "channelRegistration", Grading::fringedEdge);

    private static Optional<String> darkSide(QcInput input, QcContext context) {
        DarkCorner corner = Illumination.darkestCorner(
                input.surface(),
                context.bed().orElseGet(input.surface()::bounds),
                context.avoid().orElse(List.of()));
        if (!Double.isFinite(corner.mean())) {
            return Optional.empty();
        }
        return Optional.of("the " + corner.corner() + " is the dark side, " + NumberText.fixed(corner.under() * 100, 1)
                + " percent under the brightest");
    }

    private static Optional<String> castWay(QcInput input, QcContext context) {
        return context.target()
                .flatMap(target -> GrayBalance.castDirection(input.surface(), target.patches()))
                .map(cast -> "the cast is " + (cast.amount() > 0 ? "towards" : "away from") + " " + cast.channel());
    }

    private static Optional<String> noisyChannel(QcInput input, QcContext context) {
        if (!input.surface().layout().isColour()) {
            return Optional.empty();
        }
        return context.target()
                .flatMap(target -> Patches.lightestFlatPatch(input.surface(), target.patches()))
                .flatMap(Noise::noisiestChannel)
                .filter(read -> Double.isFinite(read.snr()))
                .map(read ->
                        "the noise is worst in " + read.channel() + ", at " + NumberText.fixed(read.snr(), 1) + " dB");
    }

    private static Optional<String> bothAxes(QcInput input, QcContext context) {
        return context.sampling()
                .filter(sampling -> !sampling.isSquare())
                .map(sampling -> "the capture is " + NumberText.fixed(sampling.x(), 1) + " ppi across and "
                        + NumberText.fixed(sampling.y(), 1) + " ppi down, so check the stepper as well");
    }

    private static Optional<String> fringedEdge(QcInput input, QcContext context) {
        List<Double> shifts = Sharpness.channelShifts(
                input.surface(),
                context.sheet().orElseThrow(),
                StripOptions.defaults().reach());
        if (shifts.size() != EDGES.size()) {
            return Optional.empty();
        }
        int at = 0;
        for (int i = 1; i < shifts.size(); i += 1) {
            if (shifts.get(i) > shifts.get(at)) {
                at = i;
            }
        }
        return Optional.of("the fringe is widest on the " + EDGES.get(at) + " edge of the sheet");
    }

    /**
     * The grading written out for a person, with no item named.
     *
     * @see #reportOn(QcInput, GradingOptions)
     */
    public static List<String> reportOn(QcInput input) {
        return reportOn(input, GradingOptions.defaults());
    }

    /**
     * The same grading written out for a person: {@link Reports#report(List, ReportOptions)} against
     * the level the profile wanted, with the advice indented under the findings.
     */
    public static List<String> reportOn(QcInput input, GradingOptions options) {
        GradedCapture out = gradeCapture(input, options.qc());
        List<String> lines = new ArrayList<>(Reports.report(out.result().findings(), reportOptions(out, options)));
        out.advice().forEach(line -> lines.add("  " + line));
        return List.copyOf(lines);
    }

    /**
     * The grading written out for a machine, with no item named.
     *
     * @see #gradingToJson(QcInput, GradingOptions)
     */
    public static Map<String, Object> gradingToJson(QcInput input) {
        return gradingToJson(input, GradingOptions.defaults());
    }

    /**
     * The same grading written out for a machine, with the advice folded in so a sidecar carries
     * everything the report showed: {@link Reports#toJson(List, ReportOptions)} with {@code stars},
     * {@code level} and {@code passed} taken from the run, then {@code missing} and {@code advice}
     * after it. A new map the caller may keep, every number a {@link Double}.
     *
     * <p>The level comes off the runner rather than off the findings. They disagree on exactly one
     * case and it is the case that matters: a capture whose required checks could not be run has no
     * level at all, and a sidecar that says four stars because the four checks that did run were fine
     * would be evidence of something that was never measured.
     */
    public static Map<String, Object> gradingToJson(QcInput input, GradingOptions options) {
        GradedCapture out = gradeCapture(input, options.qc());
        QcResult result = out.result();
        Map<String, Object> json = Reports.toJson(result.findings(), reportOptions(out, options));
        json.put("stars", (double) result.stars());
        json.put("level", Levels.levelName(result.stars()));
        json.put("passed", result.passed());
        json.put("missing", new ArrayList<Object>(result.missing()));
        json.put("advice", new ArrayList<Object>(out.advice()));
        return json;
    }

    private static ReportOptions reportOptions(GradedCapture out, GradingOptions options) {
        return new ReportOptions(out.result().wanted(), options.item());
    }
}
