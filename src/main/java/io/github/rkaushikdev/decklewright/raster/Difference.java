package io.github.rkaushikdev.decklewright.raster;

/**
 * How far apart two regions of the same shape are, sample by sample.
 *
 * @param rms the root mean square of the differences
 * @param peak the largest difference
 * @param count how many samples were compared
 * @see Stats#difference(Raster, Raster)
 */
public record Difference(double rms, int peak, int count) {}
