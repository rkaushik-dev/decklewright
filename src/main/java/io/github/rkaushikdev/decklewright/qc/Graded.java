package io.github.rkaushikdev.decklewright.qc;

/**
 * Anything graded at a star level: a finding, a capture's level, a run's result, a batch. The
 * batch tallies in {@link Levels} take any of them, because a funder asks the same question of each.
 */
public interface Graded {
    /** The level, from zero to {@link Check#MAX_STARS}. */
    int stars();
}
