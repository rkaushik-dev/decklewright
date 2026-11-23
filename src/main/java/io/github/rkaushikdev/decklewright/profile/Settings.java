package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.measure.Axis;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turning a profile into the arguments each stage actually takes.
 *
 * <p>The stages below take plain option records and know nothing about profiles. That is
 * deliberate: it keeps them testable without building a profile, and it keeps the profile
 * vocabulary in one place. This class is that place, and it is the only thing that reads a profile
 * and writes stage options.
 *
 * <p>Lengths become pixels here, so every stage below works in pixels only.
 */
public final class Settings {

    /** A placeholder in a naming pattern, such as {@code {accession}}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");

    private Settings() {}

    /** What the scanner was set to, on both axes. */
    public static Sampling claimedSampling(Profile profile) {
        return Sampling.of(profile.capture().claimedPpi());
    }

    /** What the detection stage takes, with the scanner's claimed frequency. */
    public static CaptureOptions captureOptions(Profile profile) {
        Profile.Capture capture = profile.capture();
        return new CaptureOptions(
                capture.sigma(),
                capture.speckle(),
                capture.gap(),
                capture.expectNeatline(),
                capture.barLength().value(),
                capture.barLength().unit(),
                claimedSampling(profile));
    }

    /**
     * The four lengths in pixels at the frequency, the top and bottom down the bed and the left and
     * right across it, so a frequency that differs per axis is honoured.
     */
    public static Insets edgesInPixels(Edges edges, Sampling sampling) {
        return new Insets(
                pixels(edges.top(), sampling, Axis.Y),
                pixels(edges.right(), sampling, Axis.X),
                pixels(edges.bottom(), sampling, Axis.Y),
                pixels(edges.left(), sampling, Axis.X));
    }

    /**
     * What the planner takes.
     *
     * @param sampling what the capture was really taken at, which the bleed and safe area are
     *     worked out at
     */
    public static CropOptions cropOptions(Profile profile, Sampling sampling) {
        Profile.Crop crop = profile.crop();
        return new CropOptions(
                crop.anchor(),
                edgesInPixels(crop.bleed(), sampling),
                edgesInPixels(crop.safeArea(), sampling),
                crop.aspect(),
                crop.aspectDirection(),
                crop.minimumPpi(),
                crop.snapTo(),
                crop.snapDirection(),
                crop.rounding(),
                crop.background(),
                crop.deskew(),
                crop.deskewAbove());
    }

    /** What the derive stage takes, one recipe per derivative in the order written. */
    public static List<DerivativeRecipe> derivativeRecipes(Profile profile) {
        return profile.derivatives().stream()
                .map(derivative -> new DerivativeRecipe(
                        derivative.name(),
                        derivative.format(),
                        derivative.longestSide(),
                        derivative.scale(),
                        derivative.ppi(),
                        derivative.depth().bits(),
                        derivative.layout(),
                        derivative.kernel(),
                        derivative.keepMetadata()))
                .toList();
    }

    /** What the quality checks take. */
    public static QcLimits qcLimits(Profile profile) {
        Profile.Qc qc = profile.qc();
        return new QcLimits(
                Sampling.of(qc.samplingPpi()),
                qc.samplingTolerance(),
                qc.skewTolerance(),
                qc.illuminationTolerance(),
                qc.grayBalanceTolerance(),
                qc.minimumSnr(),
                qc.maximumClipping(),
                qc.minimumStars(),
                qc.toneAims(),
                qc.toneTolerance());
    }

    /**
     * Fill a naming pattern, lower casing the result when the profile says to. An unknown
     * placeholder is left alone rather than blanked, so a typo shows up in the file name instead of
     * quietly producing two files with the same one.
     *
     * @param values what each placeholder stands for; a number is written as reports write one
     */
    public static String fillName(Profile profile, Map<String, ?> values) {
        Matcher placeholders = PLACEHOLDER.matcher(profile.naming().pattern());
        String filled = placeholders.replaceAll(match -> {
            String key = match.group(1);
            String text = values.containsKey(key) ? text(values.get(key)) : match.group();
            return Matcher.quoteReplacement(text);
        });
        return profile.naming().lowercase() ? filled.toLowerCase(Locale.ROOT) : filled;
    }

    /** Every stage's arguments at once, at the frequency the scanner claims. */
    public static StageSettings settingsFor(Profile profile) {
        return settingsFor(profile, claimedSampling(profile));
    }

    /**
     * Every stage's arguments at once.
     *
     * @param sampling a measured frequency, which is used over the claimed one
     */
    public static StageSettings settingsFor(Profile profile, Sampling sampling) {
        return new StageSettings(
                profile,
                sampling,
                captureOptions(profile),
                cropOptions(profile, sampling),
                derivativeRecipes(profile),
                qcLimits(profile));
    }

    private static double pixels(Length length, Sampling sampling, Axis axis) {
        return sampling.toPixels(length.value(), length.unit(), axis);
    }

    private static String text(Object value) {
        return value instanceof Number number ? NumberText.of(number) : String.valueOf(value);
    }
}
