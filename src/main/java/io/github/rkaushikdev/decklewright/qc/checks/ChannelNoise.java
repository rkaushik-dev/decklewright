package io.github.rkaushikdev.decklewright.qc.checks;

import java.util.Objects;

/**
 * The colour channel a flat patch is noisiest in, which tells an operator whether it is the gain or
 * the light that wants changing: noise in every channel is the exposure, noise in blue alone is warm
 * lamps.
 *
 * @param channel the channel's name, such as {@code blue}
 * @param snr its signal to noise, in decibels; positive infinity for a channel with no variation
 * @see Noise#noisiestChannel(io.github.rkaushikdev.decklewright.raster.Raster)
 */
public record ChannelNoise(String channel, double snr) {
    /** A reading of the given parts. */
    public ChannelNoise {
        Objects.requireNonNull(channel, "channel");
    }
}
