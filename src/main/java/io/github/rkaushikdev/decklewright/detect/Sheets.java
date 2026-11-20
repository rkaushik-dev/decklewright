package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finding the sheet, from a capture to a quad.
 *
 * <p>Everything above this calls this one function, and everything below it is a step this one
 * arranges. The steps are all separately testable and the order matters: clean before labelling,
 * label before fitting, and fit before asking any of the three skew measures what they think.
 *
 * <p>The three measures are the point. A single number with no way to tell whether it is right is
 * worse than no number at all, because the operator cannot see a wrong crop until the derivatives
 * are already made.
 */
public final class Sheets {
    /** A finding less confident than this is not used unchecked, unless the caller says otherwise. */
    private static final double DEFAULT_MINIMUM_CONFIDENCE = 0.5;

    /** A sheet taking more of the capture than this leaves no bed around it to read. */
    private static final double WHOLE_BED = 0.95;

    private Sheets() {}

    /**
     * Finds the sheet with the default options.
     *
     * @see #findSheet(Surface, SheetOptions)
     */
    public static SheetFinding findSheet(Surface surface) {
        return findSheet(surface, SheetOptions.defaults());
    }

    /**
     * Finds the sheet: everything that stands out from the bed, opened to remove dust, closed to
     * mend torn edges and with its holes filled, then the largest blob of that, fitted three ways.
     *
     * @throws BadArgumentError when the minimum coverage is outside zero to one, or the fraction
     *     outside 0.001 to 0.45
     * @throws DetectionError when nothing stands out from the bed, nothing survives the cleanup, or
     *     what is left is too small to be a sheet
     */
    public static SheetFinding findSheet(Surface surface, SheetOptions options) {
        double minimumCoverage = Guards.requireInRange(options.minimumCoverage(), 0, 1, "minimumCoverage");

        // A fraction of zero means the default border, rather than no border at all.
        double fraction = options.fraction();
        boolean fractionGiven = fraction != 0 && !Double.isNaN(fraction);
        Mask raw = fractionGiven
                ? Backgrounds.objectMask(surface, options.sigma(), fraction).mask()
                : Backgrounds.objectMask(surface, options.sigma()).mask();
        long before = raw.count();
        if (before == 0) {
            throw new DetectionError(
                    "nothing on the bed stands out from it",
                    Map.of("size", surface.size().toJson()));
        }

        Mask cleaned = Morphology.fillHoles(Morphology.close(Morphology.open(raw, options.speckle()), options.gap()));
        ComponentMask found = Components.largestComponent(cleaned)
                .orElseThrow(() -> new DetectionError("nothing survived the cleanup", Map.of("before", before)));

        double coverage = found.component().area() / ((double) surface.width() * surface.height());
        if (coverage < minimumCoverage) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("coverage", coverage);
            details.put("minimumCoverage", minimumCoverage);
            throw new DetectionError("what is on the bed is too small to be a sheet", details);
        }

        Mask sheet = found.mask();
        FittedQuad caliper = QuadFit.fitQuad(sheet);
        SideLines lines = Edges.edgeLines(sheet);
        SkewReadings readings =
                new SkewReadings(caliper.angle(), Deskew.measureSkew(sheet).angle(), Edges.skewFromLines(lines));
        double spread = spreadOf(readings);
        RefinedCorners refined = Corners.refineCorners(caliper.quad(), lines);

        double agreement = options.agreement();
        double agreementScore = spread <= agreement ? 1 : agreement / spread;
        double confidence = Math.min(Math.min(Corners.quadConfidence(lines), caliper.fill()), agreementScore);

        return new SheetFinding(
                refined.quad(),
                readings.calipers(),
                sheet,
                confidence,
                spread,
                found.component().touchesEdge(),
                coverage,
                readings);
    }

    /** The degrees between the furthest apart pair of the three skew measures. */
    public static double spreadOf(SkewReadings readings) {
        double calipers = readings.calipers().degrees();
        double projection = readings.projection().degrees();
        double sides = readings.sides().degrees();
        return Math.max(Math.max(calipers, projection), sides) - Math.min(Math.min(calipers, projection), sides);
    }

    /**
     * A one line summary for the run log, such as {@code 124x88, skew 0.00 deg, spread 0.12,
     * confidence 0.93}. Operators read these by the hundred and the only thing they want is whether
     * to go and look.
     */
    public static String describeFinding(SheetFinding finding) {
        Rect size = finding.quad().boundingRect();
        List<String> parts = new ArrayList<>();
        parts.add(NumberText.of(Num.roundHalfUp(size.width())) + "x" + NumberText.of(Num.roundHalfUp(size.height())));
        parts.add("skew " + NumberText.fixed(finding.skew().degrees(), 2) + " deg");
        parts.add("spread " + NumberText.fixed(finding.spread(), 2));
        parts.add("confidence " + NumberText.fixed(finding.confidence(), 2));
        if (finding.touchesEdge()) {
            parts.add("runs off the bed");
        }
        return String.join(", ", parts);
    }

    /**
     * The reasons not to use a finding unchecked, with a confidence floor of a half.
     *
     * @see #objections(SheetFinding, double)
     */
    public static List<String> objections(SheetFinding finding) {
        return objections(finding, DEFAULT_MINIMUM_CONFIDENCE);
    }

    /**
     * The reasons this finding should not be used unchecked, in a fixed order; empty for a clean
     * finding.
     *
     * @param minimumConfidence the confidence below which the fit is objected to
     */
    public static List<String> objections(SheetFinding finding, double minimumConfidence) {
        List<String> out = new ArrayList<>();
        if (finding.confidence() < minimumConfidence) {
            out.add("the sides do not fit well");
        }
        if (finding.touchesEdge()) {
            out.add("the sheet runs off the bed");
        }
        if (!finding.quad().isConvex()) {
            out.add("the corners fold back on themselves");
        }
        if (finding.coverage() > WHOLE_BED) {
            out.add("the sheet fills the whole bed, so there is no margin");
        }
        return List.copyOf(out);
    }
}
