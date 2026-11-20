package io.github.rkaushikdev.decklewright.store;

import java.util.Objects;

/**
 * What a workspace holds, read off its ledger rather than off its folders.
 *
 * @param summary the entries, counted
 * @param unreadable how many lines of the ledger could not be read
 * @see Dispatch#shiftReport(Workspace)
 */
public record ShiftReport(Summary summary, int unreadable) {

    /** A report of the given parts. */
    public ShiftReport {
        Objects.requireNonNull(summary, "summary");
    }
}
