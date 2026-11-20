package io.github.rkaushikdev.decklewright.qc.checks;

import java.util.Objects;

/**
 * Which way a colour cast goes, so an operator can fix it rather than just knowing there is one.
 *
 * @param channel {@code red}, {@code green} or {@code blue}: the channel furthest from neutral
 * @param amount how far, in sample values on an average patch; positive when it runs high, negative
 *     when it runs low
 * @see GrayBalance#castDirection(io.github.rkaushikdev.decklewright.raster.Surface, java.util.List)
 */
public record Cast(String channel, double amount) {
    /** A cast of the given parts. */
    public Cast {
        Objects.requireNonNull(channel, "channel");
    }
}
