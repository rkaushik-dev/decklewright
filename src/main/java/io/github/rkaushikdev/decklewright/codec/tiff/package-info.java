/**
 * Baseline tiff as the flatbed writes it: the header and directory entries, the strip reader, and
 * the two compression schemes the reader takes.
 *
 * <p>{@link io.github.rkaushikdev.decklewright.codec.tiff.Tags} reads the header and a directory
 * into {@link io.github.rkaushikdev.decklewright.codec.tiff.Entry}s; {@link
 * io.github.rkaushikdev.decklewright.codec.tiff.TiffReader} turns the first directory and its strips
 * into a surface; {@link io.github.rkaushikdev.decklewright.codec.tiff.PackBits} and {@link
 * io.github.rkaushikdev.decklewright.codec.tiff.Lzw} decode the strips, and each has a matching
 * encoder so it can be tested against itself. Tiff is read here and never written.
 */
package io.github.rkaushikdev.decklewright.codec.tiff;
