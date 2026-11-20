package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Units;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Saying in plain words what a profile will do.
 *
 * <p>A curator writes a profile and cannot run a batch to see what it means. This prints the
 * answer: what gets cropped to, how much paper is kept, what comes out, and where the quality bar
 * sits. It reads off the resolved profile rather than the file, so an inherited setting shows up
 * the same as a written one.
 */
public final class Diagnostics {
    private Diagnostics() {}

    /**
     * One derivative in a line, such as {@code service: longest side 3000 px, rgb at 8 bit, png by
     * catmullRom}.
     */
    public static String describeDerivative(Profile.Derivative derivative) {
        String size;
        if (derivative.longestSide().isPresent()) {
            size = "longest side " + NumberText.of(derivative.longestSide().getAsDouble()) + " px";
        } else if (derivative.scale().isPresent()) {
            size = "scaled to " + NumberText.of(derivative.scale().getAsDouble());
        } else {
            size = "full size";
        }
        String bits = derivative.layout() + " at " + derivative.depth() + " bit";
        String extra = derivative.keepMetadata() ? ", keeping metadata" : "";
        return derivative.name() + ": " + size + ", " + bits + ", " + derivative.format() + " by " + derivative.kernel()
                + extra;
    }

    /** The whole profile in lines: its name, the crop, the capture, each derivative, the quality bar. */
    public static List<String> describeProfile(Profile profile) {
        Profile.Crop crop = profile.crop();
        Profile.Capture capture = profile.capture();
        Profile.Qc qc = profile.qc();
        List<String> lines = new ArrayList<>();
        lines.add(profile.name() + ": " + profile.title() + " (version " + NumberText.of(profile.version()) + ")");

        lines.add("crops to the " + crop.anchor() + (crop.deskew() ? ", straightened first" : ""));
        if (!crop.bleed().isZero()) {
            lines.add("keeps " + describeEdges(crop.bleed()) + " beyond it");
        }
        if (!crop.safeArea().isZero()) {
            lines.add("holds a safe area of " + describeEdges(crop.safeArea()));
        }
        crop.aspect()
                .ifPresent(aspect -> lines.add(
                        "forces an aspect of " + NumberText.of(aspect) + ", " + crop.aspectDirection() + "ing"));
        lines.add("rounds " + crop.rounding() + " and snaps to " + NumberText.of(crop.snapTo()) + " px "
                + crop.snapDirection());
        lines.add("refuses anything under " + NumberText.of(crop.minimumPpi()) + " ppi");

        lines.add("capture claimed at " + NumberText.of(capture.claimedPpi()) + " ppi");
        if (capture.expectNeatline()) {
            lines.add("looks for a neatline");
        }

        for (Profile.Derivative derivative : profile.derivatives()) {
            lines.add(describeDerivative(derivative));
        }
        if (profile.derivatives().isEmpty()) {
            lines.add("writes nothing");
        }

        lines.add("quality: " + NumberText.of(qc.samplingPpi()) + " ppi " + qc.samplingTolerance() + ", skew under "
                + NumberText.of(qc.skewTolerance()) + " deg, " + qc.minimumStars() + " stars");
        return List.copyOf(lines);
    }

    /**
     * What the crop settings come to in pixels at the frequency the scanner claims.
     *
     * @see #describeInPixels(Profile, double)
     */
    public static List<String> describeInPixels(Profile profile) {
        return inPixels(Settings.settingsFor(profile));
    }

    /**
     * What the crop settings come to in pixels for a given capture. A curator writes millimetres and
     * this is what the planner will actually use, which is the number they want when a crop looks
     * wrong by a hair.
     *
     * @param ppi the frequency the capture was taken at
     * @throws BadArgumentError when the frequency is not a finite number above zero
     */
    public static List<String> describeInPixels(Profile profile, double ppi) {
        return inPixels(Settings.settingsFor(profile, Sampling.of(ppi)));
    }

    /**
     * Everything about one profile, ready to print, at the frequency the scanner claims.
     *
     * @see #report(Profile, double)
     */
    public static List<String> report(Profile profile) {
        List<Problem> problems = Validation.inspectProfile(profile);
        return joined(describeProfile(profile), describeInPixels(profile), Validation.describeProblems(problems));
    }

    /**
     * Everything about one profile, ready to print: what it does, what that is in pixels at the
     * frequency, then any problems with it.
     *
     * @throws BadArgumentError when the frequency is not a finite number above zero
     */
    public static List<String> report(Profile profile, double ppi) {
        List<Problem> problems = Validation.inspectProfile(profile);
        return joined(describeProfile(profile), describeInPixels(profile, ppi), Validation.describeProblems(problems));
    }

    private static List<String> inPixels(StageSettings settings) {
        Insets bleed = settings.crop().bleed().rounded();
        Insets safe = settings.crop().safeArea().rounded();
        return List.of(
                "at " + settings.sampling(),
                "bleed " + slashed(bleed) + " px",
                "safe area " + slashed(safe) + " px",
                "snap to " + NumberText.of(settings.crop().snapTo()) + " px "
                        + settings.crop().snapDirection());
    }

    /** One figure when every edge is the same length, in any unit, otherwise each edge by name. */
    private static String describeEdges(Edges edges) {
        Length first = edges.top();
        if (Edges.SIDES.stream().allMatch(side -> edges.side(side).approxEquals(first))) {
            return formatted(first);
        }
        return Edges.SIDES.stream()
                .map(side -> side + " " + formatted(edges.side(side)))
                .collect(Collectors.joining(", "));
    }

    private static String formatted(Length length) {
        return Units.formatLength(length.value(), length.unit());
    }

    private static String slashed(Insets insets) {
        return DoubleStream.of(insets.top(), insets.right(), insets.bottom(), insets.left())
                .mapToObj(NumberText::of)
                .collect(Collectors.joining("/"));
    }

    private static List<String> joined(List<String> described, List<String> inPixels, List<String> problems) {
        return Stream.of(described, inPixels, problems).flatMap(List::stream).toList();
    }
}
