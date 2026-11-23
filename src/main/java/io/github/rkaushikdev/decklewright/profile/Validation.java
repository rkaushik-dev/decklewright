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
import java.util.OptionalDouble;
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
    /**
     * The placeholders a run fills: the accession and the derivative always, and the parts of a map
     * call number when the accession is written as one.
     */
    private static final List<String> FILLED = List.of("accession", "derivative", "class", "cutter", "year");

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

        if (crop.deskewAbove() > 0 && !crop.deskew()) {
            problems.add(note("crop.deskewAbove", "has no effect, the crop never straightens a sheet"));
        }
        if (crop.deskew() && crop.deskewAbove() > qc.skewTolerance()) {
            problems.add(note(
                    "crop.deskewAbove",
                    "leaves sheets up to " + NumberText.of(crop.deskewAbove()) + " deg off square, past the "
                            + NumberText.of(qc.skewTolerance()) + " the skew check allows"));
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
        for (int i = 0; i < profile.derivatives().size(); i += 1) {
            OptionalDouble ppi = profile.derivatives().get(i).ppi();
            if (ppi.isPresent() && ppi.getAsDouble() >= qc.samplingPpi()) {
                problems.add(note(
                        "derivatives[" + i + "].ppi",
                        "asks for " + NumberText.of(ppi.getAsDouble()) + " ppi from a capture taken at "
                                + NumberText.of(qc.samplingPpi()) + ", so it is written full size"));
            }
        }

        if (profile.derivatives().isEmpty()) {
            problems.add(note("derivatives", "is empty, so a run will plan a crop and write nothing"));
        }

        List<String> unfilled = Settings.placeholdersIn(profile.naming().pattern()).stream()
                .filter(name -> !FILLED.contains(name))
                .toList();
        if (!unfilled.isEmpty()) {
            problems.add(note(
                    "naming.pattern",
                    "names " + String.join(" and ", unfilled) + ", which nothing in a run fills, so it stays in every"
                            + " file name"));
        }

        if (!steppingOneWay(qc.toneAims())) {
            problems.add(fault("qc.toneAims", "does not step one way, so no wedge can read it"));
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

            List<String> sizes = sizeInstructions(derivative);
            if (sizes.size() > 1) {
                String hold = sizes.size() == 2 ? "which cannot both hold" : "which cannot all hold";
                problems.add(fault(path, "sets " + String.join(" and ", sizes) + ", " + hold));
            }
            if (sizes.isEmpty()) {
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

    /** What a derivative says about its size, in the order a longest side, a scale and a ppi. */
    private static List<String> sizeInstructions(Profile.Derivative derivative) {
        List<String> out = new ArrayList<>();
        if (derivative.longestSide().isPresent()) {
            out.add("a longest side");
        }
        if (derivative.scale().isPresent()) {
            out.add("a scale");
        }
        if (derivative.ppi().isPresent()) {
            out.add("a ppi");
        }
        return out;
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
