package io.github.rkaushikdev.decklewright.detect;

/**
 * The strongest trough in a darkness profile: where a ruled line most likely runs.
 *
 * @param at the index of the trough in the profile, or minus one when nothing rose above the run's
 *     median
 * @param contrast how far the trough rose above the run's median, in sample values; zero when
 *     nothing did
 * @see Neatlines#strongestLine(double[], int, int)
 */
public record Trough(int at, double contrast) {}
