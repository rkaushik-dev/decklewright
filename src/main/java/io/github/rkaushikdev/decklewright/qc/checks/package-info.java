/**
 * The standard quality checks and the ladders they grade against.
 *
 * <p>Each class holds one area of the funder's guidelines: the sampling frequency and the skew
 * ({@link io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew}), how evenly the bed is lit
 * ({@link io.github.rkaushikdev.decklewright.qc.checks.Illumination}), the cast on the step wedge
 * ({@link io.github.rkaushikdev.decklewright.qc.checks.GrayBalance}), noise and clipping ({@link
 * io.github.rkaushikdev.decklewright.qc.checks.Noise}), and focus across the sheet edge ({@link
 * io.github.rkaushikdev.decklewright.qc.checks.Sharpness}). Each check is made by a factory such as
 * {@code noiseCheck()}, and each ladder is a read only list of rungs, best first, written the way
 * the guidelines write it.
 *
 * <p>Alongside the checks are the readings a failed check should have come with: which corner is
 * darkest and which way a cast goes, which is what an operator needs in order to fix the stand.
 */
package io.github.rkaushikdev.decklewright.qc.checks;
