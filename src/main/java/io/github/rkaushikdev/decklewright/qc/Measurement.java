package io.github.rkaushikdev.decklewright.qc;

import java.util.Objects;

/**
 * What one check's body measured, before {@link Check#runCheck(Check, QcContext, int)} sets it
 * against the star floor.
 *
 * @param measured the reading, in the check's own units; not a number when nothing could be read
 * @param stars the level the reading earns on the check's ladder
 * @param note one line for the report, with the reading and the limit it was held to
 */
public record Measurement(double measured, int stars, String note) {
    /** A measurement of the given parts. */
    public Measurement {
        Objects.requireNonNull(note, "note");
    }
}
