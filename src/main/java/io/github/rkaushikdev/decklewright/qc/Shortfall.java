package io.github.rkaushikdev.decklewright.qc;

import java.util.Objects;

/**
 * One check holding a delivery below the level it wants.
 *
 * @param name the check
 * @param stars the level it reached
 * @param shortBy how many stars short of the wanted level it fell
 * @see Levels#shortfall(java.util.List, int)
 */
public record Shortfall(String name, int stars, int shortBy) {
    /** A shortfall of the given parts. */
    public Shortfall {
        Objects.requireNonNull(name, "name");
    }
}
