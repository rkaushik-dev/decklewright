package io.github.rkaushikdev.decklewright.manifest;

import java.util.Objects;

/**
 * Where a provenance chain breaks, and why.
 *
 * @param at the index of the event that is wrong
 * @param reason what is wrong with it, in words
 * @see Provenance#breaksIn(java.util.List)
 */
public record ChainBreak(int at, String reason) {

    /** A break of the given parts. */
    public ChainBreak {
        Objects.requireNonNull(reason, "reason");
    }
}
