package io.github.rkaushikdev.decklewright.qc;

import java.util.Objects;
import java.util.Optional;

/**
 * How a report on a set of findings is written.
 *
 * @param wanted the level the profile asked for; three stars by default
 * @param item what was measured, for the head of the report, such as an accession; an empty name
 *     is left off the lines a person reads
 * @see Reports
 */
public record ReportOptions(int wanted, Optional<String> item) {
    /** Options of the given parts. */
    public ReportOptions {
        Objects.requireNonNull(item, "item");
    }

    /** Three stars wanted, and no item named. */
    public static ReportOptions defaults() {
        return new ReportOptions(Check.DEFAULT_MINIMUM_STARS, Optional.empty());
    }

    /** These options measuring against another level. */
    public ReportOptions withWanted(int wanted) {
        return new ReportOptions(wanted, item);
    }

    /** These options naming what was measured. */
    public ReportOptions withItem(String item) {
        return new ReportOptions(wanted, Optional.of(item));
    }
}
