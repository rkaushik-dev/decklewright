package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import java.util.Objects;

/**
 * What the detection stage takes from a profile when it reads a capture.
 *
 * @param sigma how far from the bed a sample has to be to count as object
 * @param speckle the radius of the opening that removes dust from the object mask
 * @param gap the radius of the closing that mends a torn sheet edge
 * @param expectNeatline true when the capture is searched for a printed border
 * @param barLength the true length of the scale rule, in {@code barUnit}
 * @param barUnit the unit the rule's length is written in
 * @param claimed what the scanner says it captured at
 * @see Settings#captureOptions(Profile)
 */
public record CaptureOptions(
        double sigma, int speckle, int gap, boolean expectNeatline, double barLength, Unit barUnit, Sampling claimed) {

    /** Capture options of the given values. */
    public CaptureOptions {
        Objects.requireNonNull(barUnit, "barUnit");
        Objects.requireNonNull(claimed, "claimed");
    }
}
