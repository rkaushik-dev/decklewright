package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reading everything off one capture.
 *
 * <p>A work order names a capture and a collection. This turns the capture into the four facts a
 * crop plan needs: where the sheet is, how far it is turned, where the neatline is if there is one,
 * and what the sampling frequency really was. Anything it could not establish comes back empty with
 * a warning rather than as a guess.
 */
public final class Captures {
    /** A sheet found with less confidence than this needs a human to look before it is planned from. */
    private static final double USABLE_CONFIDENCE = 0.5;

    private Captures() {}

    /**
     * Reads a capture with the default options: no rule length, so no measured frequency, and no
     * neatline.
     *
     * @see #readCapture(Surface, CaptureOptions)
     */
    public static CaptureReading readCapture(Surface surface) {
        return readCapture(surface, CaptureOptions.defaults());
    }

    /**
     * Reads a capture: finds the sheet, then looks among everything else on the bed for the step
     * wedge and the scale rule, measures the frequency off the rule when its length is known and
     * checks it against the claim, and finds the neatline when one is expected.
     *
     * @throws DetectionError when there is no sheet to find, which is the one fact a reading cannot
     *     do without
     * @throws BadArgumentError when the rule length is negative, or a neatline is expected on a sheet
     *     that runs off the capture
     */
    public static CaptureReading readCapture(Surface surface, CaptureOptions options) {
        SheetFinding sheet = Sheets.findSheet(
                surface,
                SheetOptions.defaults()
                        .withSigma(options.sigma())
                        .withSpeckle(options.speckle())
                        .withGap(options.gap()));
        List<String> warnings = new ArrayList<>(Sheets.objections(sheet));

        Mask everything = Backgrounds.objectMask(surface, options.sigma()).mask();
        Mask cleaned =
                Morphology.fillHoles(Morphology.close(Morphology.open(everything, options.speckle()), options.gap()));
        OptionalInt sheetId = componentIdAt(cleaned, sheet);
        Set<Integer> ignore = sheetId.isPresent() ? Set.of(sheetId.getAsInt()) : Set.of();

        Optional<TargetFinding> target =
                Targets.locateTarget(surface, cleaned, TargetOptions.defaults().withIgnore(ignore));
        if (target.isEmpty()) {
            warnings.add("no colour target on the bed");
        }

        Optional<ScaleBarFinding> scaleBar =
                ScaleBars.findScaleBar(cleaned, ScaleBarOptions.defaults().withIgnore(ignore));
        if (scaleBar.isEmpty()) {
            warnings.add("no scale bar on the bed");
        }

        Optional<Sampling> sampling = Optional.empty();
        if (scaleBar.isPresent() && isGiven(options.barLength())) {
            Sampling measured = ScaleBars.samplingFromBar(
                    scaleBar.get(), options.barLength().getAsDouble(), options.barUnit());
            sampling = Optional.of(measured);
            if (options.claimed().isPresent()) {
                ClaimCheck check =
                        ScaleBars.checkAgainstClaim(measured, options.claimed().get());
                if (!check.agrees()) {
                    warnings.add("the rule measures "
                            + NumberText.of(Num.roundHalfUp(check.measured()))
                            + " ppi against a claimed "
                            + NumberText.of(Num.roundHalfUp(check.claimed())));
                }
            }
        }

        Optional<Neatline> neatline = Optional.empty();
        if (options.expectNeatline()) {
            Neatline found =
                    Neatlines.findNeatline(surface, sheet.quad().boundingRect().outward());
            neatline = Optional.of(found);
            if (!found.complete()) {
                warnings.add("no neatline found on the "
                        + found.missing().stream().map(Side::label).collect(Collectors.joining(" or ")));
            }
        }

        return new CaptureReading(sheet, target, scaleBar, neatline, sampling, warnings);
    }

    /**
     * Which labelled blob the sheet is, so the other detectors can skip it. The centre of the fitted
     * quad is inside the sheet by construction, so a single lookup is enough.
     *
     * @return the blob's id, or empty when the pixel nearest the quad's centre is not set
     */
    public static OptionalInt componentIdAt(Mask mask, SheetFinding sheet) {
        Point centre = sheet.quad().centre();
        double x = Num.roundHalfUp(centre.x());
        double y = Num.roundHalfUp(centre.y());
        if (!(x >= 0 && y >= 0 && x < mask.width() && y < mask.height())) {
            return OptionalInt.empty();
        }
        int column = (int) x;
        int row = (int) y;
        if (mask.get(column, row) == 0) {
            return OptionalInt.empty();
        }
        int id = Components.labelComponents(mask).label(row * mask.width() + column);
        return id > 0 ? OptionalInt.of(id) : OptionalInt.empty();
    }

    /**
     * One line for each fact the reading established, then one for each warning, such as {@code
     * sheet 124x88 at 0.00 deg} and {@code warning: no scale bar on the bed}.
     */
    public static List<String> summarise(CaptureReading reading) {
        Rect box = reading.sheet().quad().boundingRect();
        List<String> lines = new ArrayList<>();
        lines.add("sheet "
                + NumberText.of(Num.roundHalfUp(box.width()))
                + "x"
                + NumberText.of(Num.roundHalfUp(box.height()))
                + " at "
                + NumberText.fixed(reading.sheet().skew().degrees(), 2)
                + " deg");
        reading.target()
                .ifPresent(target ->
                        lines.add("target over " + NumberText.of(Num.roundHalfUp(target.range())) + " levels"));
        reading.scaleBar()
                .ifPresent(bar -> lines.add("rule " + NumberText.of(Num.roundHalfUp(bar.lengthPixels())) + " px long"));
        reading.sampling().ifPresent(sampling -> lines.add("measured " + sampling));
        reading.neatline().ifPresent(neatline -> {
            Insets widths = Neatlines.collarWidths(box.outward(), neatline.rect());
            lines.add("collar "
                    + NumberText.of(widths.top())
                    + "/"
                    + NumberText.of(widths.right())
                    + "/"
                    + NumberText.of(widths.bottom())
                    + "/"
                    + NumberText.of(widths.left()));
        });
        for (String warning : reading.warnings()) {
            lines.add("warning: " + warning);
        }
        return List.copyOf(lines);
    }

    /** Whether a plan can be built without a human looking first: a confident sheet that stays on the bed. */
    public static boolean isUsable(CaptureReading reading) {
        return reading.sheet().confidence() >= USABLE_CONFIDENCE
                && !reading.sheet().touchesEdge();
    }

    /** A rule length counts only when it is there and is neither zero nor not a number. */
    private static boolean isGiven(OptionalDouble length) {
        return length.isPresent() && length.getAsDouble() != 0 && !Double.isNaN(length.getAsDouble());
    }
}
