package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.qc.checks.DarkCorner;
import io.github.rkaushikdev.decklewright.qc.checks.GrayBalance;
import io.github.rkaushikdev.decklewright.qc.checks.Illumination;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
     * does: a short illumination check adds which corner of the sheet is darkest, and a short gray
     * balance check adds which way the cast goes when there is a target to read it off.
     *
     * @param options the same options {@link QcRunner#runQc(QcInput, QcOptions)} takes
     */
    public static GradedCapture gradeCapture(QcInput input, QcOptions options) {
        QcResult out = QcRunner.runQc(input, options);
        List<String> advice = new ArrayList<>(Reports.remedies(out.findings(), out.wanted()));

        // every remedy line opens with the name of the check that fell short
        Set<String> shortOn = advice.stream()
                .map(line -> line.substring(0, line.indexOf(':')))
                .collect(Collectors.toSet());
        if (shortOn.contains("illumination")) {
            DarkCorner corner = Illumination.darkestCorner(
                    input.surface(),
                    input.reading().sheet().quad().boundingRect().outward());
            if (Double.isFinite(corner.mean())) {
                advice.add("the " + corner.corner() + " is the dark side");
            }
        }
        Optional<TargetFinding> target = input.reading().target();
        if (shortOn.contains("grayBalance") && target.isPresent()) {
            GrayBalance.castDirection(input.surface(), target.get().patches())
                    .ifPresent(cast -> advice.add(
                            "the cast is " + (cast.amount() > 0 ? "towards" : "away from") + " " + cast.channel()));
        }

        return new GradedCapture(out, advice);
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
