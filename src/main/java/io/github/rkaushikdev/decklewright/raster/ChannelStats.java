package io.github.rkaushikdev.decklewright.raster;

/**
 * The summaries of one channel over a region.
 *
 * @param min the smallest sample, or not a number when there were none
 * @param max the largest sample, or not a number when there were none
 * @param mean the mean sample, or not a number when there were none
 * @param stdDev the population standard deviation, or not a number when there were none
 * @param count how many samples were read
 * @see Stats#channelStats(Raster, int)
 */
public record ChannelStats(double min, double max, double mean, double stdDev, int count) {}
