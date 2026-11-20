package io.github.rkaushikdev.decklewright.store;

import java.util.List;

/**
 * What reading a ledger found: every line that is an entry, and how many were not.
 *
 * @param entries the entries, in the order they were appended; copied
 * @param unreadable how many lines were torn, were not json, or were json but not an entry
 * @see Ledger#readLedger(java.nio.file.Path)
 */
public record LedgerRead(List<LedgerEntry> entries, int unreadable) {

    /** A reading of the given parts; the entries are copied. */
    public LedgerRead {
        entries = List.copyOf(entries);
    }
}
