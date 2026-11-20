package io.github.rkaushikdev.decklewright.codec.png;

/**
 * The thirteen bytes of an {@code IHDR} chunk that a reader needs, as the file states them.
 *
 * @param width pixels across, an unsigned 32 bit value
 * @param height pixels down, an unsigned 32 bit value
 * @param depth bits per sample
 * @param colourType zero for gray, two for rgb, three for a palette, four for gray with alpha, six
 *     for rgba
 * @param interlace zero for none, one for Adam7
 */
public record PngHeader(long width, long height, int depth, int colourType, int interlace) {}
