package io.github.rkaushikdev.decklewright.codec.png;

/**
 * The {@code pHYs} chunk, which is where a capture keeps its sampling frequency.
 *
 * @param x pixels per unit across, an unsigned 32 bit value
 * @param y pixels per unit down, an unsigned 32 bit value
 * @param unit one for pixels per metre; zero means the ratio is known but the scale is not, which for
 *     the lab's purposes is the same as nothing
 */
public record Physical(long x, long y, int unit) {}
