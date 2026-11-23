package io.github.rkaushikdev.decklewright.qc.checks;

/**
 * What one patch of the step wedge reads: its tone, and how much the tone varies across the patch,
 * which is what decides whether the step next to it can be told apart from it.
 *
 * @param tone the mean over the patch's colour channels, in sample values
 * @param spread the mean of their standard deviations, in sample values
 * @see ToneResponse#readTone(io.github.rkaushikdev.decklewright.raster.Surface,
 *     io.github.rkaushikdev.decklewright.detect.Patch)
 */
public record PatchTone(double tone, double spread) {}
