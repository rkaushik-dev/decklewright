package io.github.rkaushikdev.decklewright.codec;

/**
 * The five things a netpbm header says.
 *
 * @param magic {@code P2}, {@code P3}, {@code P5} or {@code P6}
 * @param width pixels across
 * @param height pixels down
 * @param max the largest sample value, which also decides the depth
 * @param offset where the samples start: one byte past the end of the maximum
 */
public record PnmHeader(String magic, int width, int height, int max, int offset) {}
