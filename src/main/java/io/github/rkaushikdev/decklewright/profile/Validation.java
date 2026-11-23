package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that need more than one setting to make.
 *
 * <p>The schema can say a number is a number. It cannot say that a crop anchored to a neatline is
 * useless when the capture stage was never told to look for one, and that combination has shipped
 * twice.
 *
 * <p>Faults stop a run. Notes do not, but they go in the run log, because most of them are a profile
 * that will surprise somebody later.
 */
public final class Validation {
    private Validation() {}

    /** A problem that stops a run. */
    public static Problem fault(String path, String message) {
        return new Problem(path, message, Severity.FAULT);
    }

    /** A problem that goes in the run log and lets the run go on. */
    public static Problem note(String path, String message) {
        return new Problem(path, message, Severity.NOTE);
    }

    /** Every problem with an already parsed profile, in the order the settings are read. */
    public static List<Problem> inspectProfile(Profile profile) {
        Profile.Capture capture = profile.capture();
        Profile.Crop crop = profile.crop();
        Profile.Qc qc = profile.qc();
        List<Problem> problems = new ArrayList<>();

        if (crop.anchor() == Anchor.NEATLINE && !capture.expectNeatline()) {
            problems.add(fault("crop.anchor", "anchors to the neatline but the capture stage never looks for one"));
        }
        if (capture.expectNeatline() && crop.anchor() != Anchor.NEATLINE) {
            problems.add(note("capture.expectNeatline", "looks for a neatline that the crop ignores"));
        }

        if (crop.minimumPpi() > qc.samplingPpi()) {
            problems.add(fault(
                    "crop.minimumPpi",
                    "demands " + NumberText.of(crop.minimumPpi()) + " ppi from a capture taken at "
                            + NumberText.of(qc.samplingPpi())));
        }

        for (String side : Edges.SIDES) {
            Length bleed = crop.bleed().side(side);
            Length safeArea = crop.safeArea().side(side);
            if (bleed.isNegative()) {
                problems.add(fault("crop.bleed." + side, "is negative, which would cut into the sheet"));
            }
            if (safeArea.isNegative()) {
                problems.add(fault("crop.safeArea." + side, "is negative"));
            }
            if (!bleed.isZero() && !safeArea.isZero()) {
                problems.add(note("crop.safeArea." + side, "is set alongside a bleed, so the two will fight"));
            }
        }

        problems.addAll(inspectDerivatives(profile.derivatives()));

        if (profile.derivatives().isEmpty()) {
            problems.add(note("derivatives", "is empty, so a run will plan a crop and write nothing"));
        }

        if (!steppingOneWay(qc.toneAims())) {
            problems.add(fault("qc.toneAims", "does not step one way, so no wedge can read it"));
        }

        if (qc.minimumStars() > 3 && qc.maximumClipping() > 0.001) {
            problems.add(note("qc.maximumClipping", "is looser than four stars allows, so the level is unreachable"));
        }

        return List.copyOf(problems);
    }

    /** Every problem with a list of derivatives, each pathed by its index. */
    public static List<Problem> inspectDerivatives(List<Profile.Derivative> derivatives) {
        List<Problem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int fullSize = 0;

        for (int i = 0; i < derivatives.size(); i += 1) {
            Profile.Derivative derivative = derivatives.get(i);
            String path = "derivatives[" + i + "]";
            if (!seen.add(derivative.name())) {
                problems.add(fault(path + ".name", "repeats the name " + derivative.name()));
            }

            boolean hasSide = derivative.longestSide().isPresent();
            boolean hasScale = derivative.scale().isPresent();
            if (hasSide && hasScale) {
                problems.add(fault(path, "sets both a longest side and a scale, which cannot both hold"));
            }
            if (!hasSide && !hasScale) {
                fullSize += 1;
            }

            if (derivative.format() == OutputFormat.PNM && derivative.keepMetadata()) {
                problems.add(note(path + ".keepMetadata", "has no effect, netpbm carries no metadata"));
            }
            if (derivative.format() == OutputFormat.PNM
                    && derivative.layout() != Layout.GRAY
                    && derivative.depth() == Depth.SIXTEEN) {
                problems.add(note(path + ".format", "writes a very large file at sixteen bit colour"));
            }
        }

        if (fullSize > 1) {
            problems.add(note("derivatives", "has more than one derivative at full size"));
        }
        return List.copyOf(problems);
    }

    /** True when every aim is at least the one before it, or every aim at most the one before it. */
    private static boolean steppingOneWay(List<Double> aims) {
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < aims.size(); i += 1) {
            up &= aims.get(i) >= aims.get(i - 1);
            down &= aims.get(i) <= aims.get(i - 1);
        }
        return up || down;
    }

    /** Only the problems that stop a run, in the order given. */
    public static List<Problem> faultsOnly(List<Problem> problems) {
        return problems.stream()
                .filter(problem -> problem.severity() == Severity.FAULT)
                .toList();
    }

    /**
     * The same profile, when it has no faults.
     *
     * @throws ProfileError naming the first fault in its message, with the profile's {@code name} and
     *     every fault as {@code faults} in the details
     */
    public static Profile requireUsable(Profile profile) {
        List<Problem> faults = faultsOnly(inspectProfile(profile));
        if (!faults.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("name", profile.name());
            details.put(
                    "faults",
                    faults.stream()
                            .map(problem -> problem.path() + ": " + problem.message())
                            .toList());
            throw new ProfileError(
                    "the " + profile.name() + " profile cannot be used: "
                            + faults.get(0).message(),
                    details);
        }
        return profile;
    }

    /** Each problem as a line for the run log, such as {@code fault: crop.anchor: anchors to ...}. */
    public static List<String> describeProblems(List<Problem> problems) {
        return problems.stream()
                .map(problem -> problem.severity() + ": " + problem.path() + ": " + problem.message())
                .toList();
    }
}
