package io.github.rkaushikdev.decklewright.codec;

import java.util.Objects;

/**
 * One thing wrong with a file in the intake folder.
 *
 * @param kind what kind of trouble it is
 * @param detail what went wrong, in the words of the codec that found it
 */
public record Problem(ProblemKind kind, String detail) {
    /** A problem of the given parts. */
    public Problem {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }
}
