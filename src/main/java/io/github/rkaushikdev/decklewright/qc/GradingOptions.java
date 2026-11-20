package io.github.rkaushikdev.decklewright.qc;

import java.util.Objects;
import java.util.Optional;

/**
 * How a grading is run and written out.
 *
 * @param item what was measured, such as an accession, for the head of the report and the json
 * @param qc which checks run and which are required
 * @see Grading
 */
public record GradingOptions(Optional<String> item, QcOptions qc) {
    /** Options of the given parts. */
    public GradingOptions {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(qc, "qc");
    }

    /** No item named, and the standard checks with the standard ones required. */
    public static GradingOptions defaults() {
        return new GradingOptions(Optional.empty(), QcOptions.defaults());
    }

    /** These options naming what was measured. */
    public GradingOptions withItem(String item) {
        return new GradingOptions(Optional.of(item), qc);
    }

    /** These options running the checks another way. */
    public GradingOptions withQc(QcOptions qc) {
        return new GradingOptions(item, qc);
    }
}
