package io.github.rkaushikdev.decklewright.detect;

import java.util.List;

/**
 * What the bed looks like: the tone of the matte board behind the sheet and how much it varies.
 *
 * @param mean the mean of each channel, in channel order
 * @param stdDev the spread of each channel, in channel order
 * @param samples how many pixels the estimate was taken over
 * @see Backgrounds#estimateBackground(io.github.rkaushikdev.decklewright.raster.Surface)
 */
public record Background(List<Double> mean, List<Double> stdDev, int samples) {
    /** A background of the given parts; the lists are copied. */
    public Background {
        mean = List.copyOf(mean);
        stdDev = List.copyOf(stdDev);
    }
}
