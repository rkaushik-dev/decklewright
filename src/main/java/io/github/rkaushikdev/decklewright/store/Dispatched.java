package io.github.rkaushikdev.decklewright.store;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A delivery written and recorded.
 *
 * @param directory where the files went
 * @param entry the ledger line that records them
 * @see Dispatch#deliver(Workspace, io.github.rkaushikdev.decklewright.manifest.Payload)
 */
public record Dispatched(Path directory, LedgerEntry entry) {

    /** A dispatch of the given parts. */
    public Dispatched {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(entry, "entry");
    }
}
