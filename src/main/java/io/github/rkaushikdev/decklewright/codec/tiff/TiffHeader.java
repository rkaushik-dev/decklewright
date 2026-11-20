package io.github.rkaushikdev.decklewright.codec.tiff;

/**
 * The eight bytes a tiff opens with.
 *
 * @param littleEndian true for {@code II}, false for {@code MM}
 * @param firstIfd where the first directory starts, an unsigned 32 bit value
 */
public record TiffHeader(boolean littleEndian, long firstIfd) {}
