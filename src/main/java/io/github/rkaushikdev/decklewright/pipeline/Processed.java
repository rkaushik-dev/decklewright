package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.store.LedgerEntry;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One capture worked out of a workspace: the run, where its delivery went, and the ledger line that
 * records it.
 *
 * @param run what the run did
 * @param directory where the delivery was written; empty when nothing was, because the run failed or
 *     was held
 * @param entry the line appended to the ledger
 * @see WorkspaceRunner#process(io.github.rkaushikdev.decklewright.store.Workspace, JobInput, RunOptions)
 */
public record Processed(RunResult run, Optional<Path> directory, LedgerEntry entry) {

    /** A result of the given parts. */
    public Processed {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(entry, "entry");
    }
}
