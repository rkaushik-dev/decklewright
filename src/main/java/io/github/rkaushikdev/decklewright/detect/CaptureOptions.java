package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How a capture is read. A profile turns into one of these; nothing below reads a profile.
 *
 * @param sigma how many spreads from the bed counts as object
 * @param speckle the radius of the opening that removes dust
 * @param gap the radius of the closing that mends a torn edge
 * @param barLength the true length of the scale rule; with none, or zero, no frequency is measured
 * @param barUnit the unit the bar length is in
 * @param claimed the frequency the scanner said it captured at, to check the rule against
 * @param expectNeatline look for a ruled border on the sheet
 * @see Captures#readCapture(io.github.rkaushikdev.decklewright.raster.Surface, CaptureOptions)
 */
public record CaptureOptions(
        double sigma,
        double speckle,
        double gap,
        OptionalDouble barLength,
        Unit barUnit,
        Optional<Sampling> claimed,
        boolean expectNeatline) {
    /** Options of the given parts. */
    public CaptureOptions {
        Objects.requireNonNull(barLength, "barLength");
        Objects.requireNonNull(barUnit, "barUnit");
        Objects.requireNonNull(claimed, "claimed");
    }

    /**
     * Six spreads off the bed, an opening of two pixels and a closing of three, a rule of unknown
     * length in inches, no claim to check and no neatline.
     */
    public static CaptureOptions defaults() {
        return new CaptureOptions(
                Backgrounds.DEFAULT_SIGMA, 2, 3, OptionalDouble.empty(), Unit.INCH, Optional.empty(), false);
    }

    /** These options with another threshold off the bed. */
    public CaptureOptions withSigma(double sigma) {
        return new CaptureOptions(sigma, speckle, gap, barLength, barUnit, claimed, expectNeatline);
    }

    /** These options with another radius for removing dust. */
    public CaptureOptions withSpeckle(double speckle) {
        return new CaptureOptions(sigma, speckle, gap, barLength, barUnit, claimed, expectNeatline);
    }

    /** These options with another radius for mending torn edges. */
    public CaptureOptions withGap(double gap) {
        return new CaptureOptions(sigma, speckle, gap, barLength, barUnit, claimed, expectNeatline);
    }

    /** These options with the rule's true length, in the {@linkplain #barUnit() bar unit}. */
    public CaptureOptions withBarLength(double barLength) {
        return new CaptureOptions(sigma, speckle, gap, OptionalDouble.of(barLength), barUnit, claimed, expectNeatline);
    }

    /** These options with the rule's length read in another unit. */
    public CaptureOptions withBarUnit(Unit barUnit) {
        return new CaptureOptions(sigma, speckle, gap, barLength, barUnit, claimed, expectNeatline);
    }

    /** These options, checking the rule against the frequency the scanner claimed. */
    public CaptureOptions withClaimed(Sampling claimed) {
        return new CaptureOptions(sigma, speckle, gap, barLength, barUnit, Optional.of(claimed), expectNeatline);
    }

    /** These options, looking for a neatline or not. */
    public CaptureOptions withExpectNeatline(boolean expectNeatline) {
        return new CaptureOptions(sigma, speckle, gap, barLength, barUnit, claimed, expectNeatline);
    }
}
