package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.ScaleBarFinding;
import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.qc.checks.GrayBalance;
import io.github.rkaushikdev.decklewright.qc.checks.Illumination;
import io.github.rkaushikdev.decklewright.qc.checks.Noise;
import io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew;
import io.github.rkaushikdev.decklewright.qc.checks.Sharpness;
import io.github.rkaushikdev.decklewright.qc.checks.ToneResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Running the quality checks against a capture.
 *
 * <p>The set is the same for every collection. What changes is the limits and which checks are
 * required, and both come off the profile. A site that wants another check adds it to the set it
 * passes in rather than editing this class.
 *
 * <p>The context is assembled here, once, so a check never has to know how to get at a reading.
 * That is what makes the checks testable with a context built by hand.
 *
 * <p>It also carries a list of what is on the bed and is not bed, which the illumination check
 * needs. Without it that check measures the difference between the board and the steel rule and
 * fails every capture in the shop.
 */
public final class QcRunner {
    /** The ones a delivery cannot be graded without. */
    public static final List<String> REQUIRED = List.of("sampling", "skew", "clipping");

    private static final String TONE = "toneResponse";

    private QcRunner() {}

    /**
     * The checks that run on every capture, in the order a report lists them: sampling, skew, skew
     * agreement, illumination, gray balance, noise, clipping and sharpness.
     */
    public static List<Check> standardChecks() {
        return List.of(
                SamplingAndSkew.samplingCheck(),
                SamplingAndSkew.skewCheck(),
                SamplingAndSkew.skewAgreementCheck(),
                Illumination.illuminationCheck(),
                GrayBalance.grayBalanceCheck(),
                Noise.noiseCheck(),
                Noise.clippingCheck(),
                Sharpness.sharpnessCheck());
    }

    /**
     * Everything a check might ask for, gathered off the reading and the profile: the sheet's
     * bounding rect out to whole pixels, the sheet, target and scale bar as what to avoid, the whole
     * capture as the bed, the frequency given or else the one the reading measured, and the
     * profile's limits. Only the crop, the target and the frequency can be absent.
     */
    public static QcContext contextFor(QcInput input) {
        CaptureReading reading = input.reading();
        Rect sheet = reading.sheet().quad().boundingRect().outward();
        List<Rect> avoid = new ArrayList<>();
        avoid.add(sheet);
        reading.target().map(TargetFinding::rect).ifPresent(avoid::add);
        reading.scaleBar().map(ScaleBarFinding::rect).ifPresent(avoid::add);
        return new QcContext(
                Optional.of(input.surface()),
                Optional.of(sheet),
                Optional.of(avoid),
                Optional.of(input.surface().bounds()),
                input.crop(),
                reading.target(),
                input.sampling().or(reading::sampling),
                Optional.of(reading.sheet().skew()),
                OptionalDouble.of(reading.sheet().spread()),
                Optional.of(Settings.qcLimits(input.profile())));
    }

    /**
     * Grade a capture with the standard checks.
     *
     * @see #runQc(QcInput, QcOptions)
     */
    public static QcResult runQc(QcInput input) {
        return runQc(input, QcOptions.defaults());
    }

    /**
     * Grade a capture: run every check against the level the profile asks for, then take the level
     * once the required checks are accounted for.
     *
     * @throws io.github.rkaushikdev.decklewright.QcError when the set holds two checks of one name
     */
    public static QcResult runQc(QcInput input, QcOptions options) {
        List<Check> checks = checksFor(input.profile(), options.checks());
        Check.requireNoDuplicates(checks);

        int wanted = input.profile().qc().minimumStars();
        QcContext context = contextFor(input);
        List<Finding> findings = Check.runChecks(checks, context, wanted);
        RequiredLevel level = Levels.levelWithRequired(findings, options.required());

        return new QcResult(findings, level.stars(), level.worst(), level.missing(), level.stars() >= wanted, wanted);
    }

    /**
     * Grade a batch with the standard checks.
     *
     * @see #runBatch(List, QcOptions)
     */
    public static BatchResult runBatch(List<QcInput> items) {
        return runBatch(items, QcOptions.defaults());
    }

    /**
     * Run the same set over a batch and keep the per item results alongside the level the batch as
     * a whole delivers at.
     */
    public static BatchResult runBatch(List<QcInput> items, QcOptions options) {
        List<QcResult> results =
                items.stream().map(item -> runQc(item, options)).toList();
        int passed = (int) results.stream().filter(QcResult::passed).count();
        return new BatchResult(results, Levels.batchLevel(results), passed, results.size() - passed);
    }

    /**
     * The checks a profile asks for on top of the ones given: a profile that aims its wedge adds the
     * tone response check straight after gray balance, or at the end when gray balance is not in the
     * set. A set that already holds a check of that name is left as it is.
     */
    public static List<Check> checksFor(Profile profile, List<Check> checks) {
        boolean has = checks.stream().anyMatch(item -> item.name().equals(TONE));
        if (profile.qc().toneAims().isEmpty() || has) {
            return checks;
        }
        List<Check> out = new ArrayList<>(checks);
        int after = 0;
        while (after < out.size() && !out.get(after).name().equals("grayBalance")) {
            after += 1;
        }
        out.add(Math.min(after + 1, out.size()), ToneResponse.toneResponseCheck());
        return List.copyOf(out);
    }

    /** The checks with the named ones taken out, the rest in the order given. */
    public static List<Check> withoutChecks(List<Check> checks, List<String> names) {
        Set<String> drop = new HashSet<>(names);
        return checks.stream().filter(item -> !drop.contains(item.name())).toList();
    }
}
