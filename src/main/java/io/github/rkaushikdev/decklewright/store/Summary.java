package io.github.rkaushikdev.decklewright.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What a shift produced, counted.
 *
 * <p>The star average is over the entries that carry one, and the count of those is reported
 * beside it. An average over a set whose size is not stated is the number that gets quoted in a
 * funding report and then cannot be reproduced.
 *
 * @param total how many entries
 * @param byOutcome how many ended each way, in the order each outcome was first seen; copied
 * @param bytes how much was written altogether
 * @param graded how many carry a grading
 * @param averageStars the mean grading over those; empty when none was graded
 * @see Ledger#summarise(java.util.List)
 */
public record Summary(int total, Map<String, Integer> byOutcome, long bytes, int graded, OptionalDouble averageStars) {

    /** A summary of the given parts; the counts are copied, keeping their order. */
    public Summary {
        byOutcome = Collections.unmodifiableMap(new LinkedHashMap<>(byOutcome));
        Objects.requireNonNull(averageStars, "averageStars");
    }
}
