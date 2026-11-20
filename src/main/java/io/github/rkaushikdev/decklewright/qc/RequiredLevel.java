package io.github.rkaushikdev.decklewright.qc;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The level a delivery reaches once the checks that had to run are taken into account.
 *
 * @param stars the plain level, or zero when a required check could not run
 * @param worst the first required check that could not run, or else the plain level's worst check;
 *     empty when nothing ran
 * @param missing the required checks that could not run, in the order they were required; copied
 * @see Levels#levelWithRequired(List, List)
 */
public record RequiredLevel(int stars, Optional<String> worst, List<String> missing) implements Graded {
    /** A level of the given parts. */
    public RequiredLevel {
        Objects.requireNonNull(worst, "worst");
        missing = List.copyOf(missing);
    }
}
