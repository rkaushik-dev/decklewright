package io.github.rkaushikdev.decklewright.plan;

import java.util.List;
import java.util.Objects;

/**
 * A pair of rules known to argue with each other. They are allowed in the same set, because the
 * engine settles most of them, but the pairing is worth naming so an explanation can point at it.
 *
 * @param between the names of the rules that argue; copied
 * @param says what an explanation prints about them
 * @see RuleSets#KNOWN_TENSIONS
 */
public record Tension(List<String> between, String says) {

    /** A tension of the given parts; the names are copied. */
    public Tension {
        between = List.copyOf(between);
        Objects.requireNonNull(says, "says");
    }
}
