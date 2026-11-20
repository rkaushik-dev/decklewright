package io.github.rkaushikdev.decklewright.detect;

/**
 * A sampling frequency measured off the rule, set against the one the scanner claimed.
 *
 * @param agrees true when the error is within the tolerance either way
 * @param error the measured frequency less the claimed one, as a share of the claim; negative when
 *     the rule measured less
 * @param measured the frequency measured off the rule across the capture, in pixels per inch
 * @param claimed the frequency the scanner claimed across the capture, in pixels per inch
 * @see ScaleBars#checkAgainstClaim(io.github.rkaushikdev.decklewright.measure.Sampling,
 *     io.github.rkaushikdev.decklewright.measure.Sampling)
 */
public record ClaimCheck(boolean agrees, double error, double measured, double claimed) {}
