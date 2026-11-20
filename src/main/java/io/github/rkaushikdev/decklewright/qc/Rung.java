package io.github.rkaushikdev.decklewright.qc;

/**
 * One rung of a ladder: the stars a measurement earns when it meets the limit. A ladder is a list
 * of rungs, best first, written the way the guidelines write it so a reader can check one against
 * the other.
 *
 * @param stars the level this rung awards
 * @param limit the measurement it takes to reach it, inclusive
 * @see Check#starsFor(double, java.util.List, boolean)
 */
public record Rung(int stars, double limit) {}
