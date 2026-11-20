package io.github.rkaushikdev.decklewright.qc;

import java.util.Objects;
import java.util.Optional;

/**
 * The level a set of findings reaches: the worst check that ran.
 *
 * @param stars the worst level among the checks that ran; zero when none did
 * @param worst the first check at that level; empty when none ran
 * @param ran how many checks ran
 * @param skipped how many could not
 * @see Levels#levelOf(java.util.List)
 */
public record Level(int stars, Optional<String> worst, int ran, int skipped) implements Graded {
    /** A level of the given parts. */
    public Level {
        Objects.requireNonNull(worst, "worst");
    }
}
