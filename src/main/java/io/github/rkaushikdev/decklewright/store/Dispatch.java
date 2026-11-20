package io.github.rkaushikdev.decklewright.store;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.manifest.Payload;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The calls a run makes on a workspace: deliver what it made, or record that it made nothing, and
 * report on the shift.
 */
public final class Dispatch {

    private Dispatch() {}

    /**
     * Write a delivery into delivered and record it.
     *
     * @see #deliver(Workspace, Payload, DeliverOptions)
     */
    public static Dispatched deliver(Workspace workspace, Payload payload) {
        return deliver(workspace, payload, DeliverOptions.defaults());
    }

    /**
     * Write a delivery and record it, which is the pair that must not come apart.
     *
     * <p>The ledger line goes down after the files, never before. A line saying an accession was
     * delivered when it was not sends somebody looking for a folder that is not there. Files on the
     * disk with no line beside them are harmless by comparison: the folder is its own evidence. The
     * line records the size actually written and the delivery's digest, so a folder can be traced to
     * a line, and its outcome is a rejection when the delivery went to rejected.
     *
     * @return where the files went, and the line
     * @throws StoreError when the files could not be written, in which case no line is written
     */
    public static Dispatched deliver(Workspace workspace, Payload payload, DeliverOptions options) {
        WrittenSet written = workspace.put(payload, options.folder(), options.overwrite());
        String outcome = options.folder() == Folder.REJECTED ? "rejected" : "delivered";
        EntryOptions line = new EntryOptions(
                options.at(),
                options.profile(),
                options.stars(),
                OptionalLong.of(written.bytes()),
                Optional.of(payload.digest()),
                Optional.empty(),
                options.warnings());
        LedgerEntry entry =
                Ledger.appendEntry(workspace.paths().ledger(), Ledger.entryFor(payload.accession(), outcome, line));
        return new Dispatched(written.directory(), entry);
    }

    /**
     * Record a run that produced nothing, as a failure.
     *
     * @see #recordFailure(Workspace, String, String, FailureOptions)
     */
    public static LedgerEntry recordFailure(Workspace workspace, String accession, String reason) {
        return recordFailure(workspace, accession, reason, FailureOptions.defaults());
    }

    /**
     * Record a run that produced nothing, so a failure is as visible as a success.
     *
     * @param reason why, in words
     * @return the line that was appended
     * @throws StoreError when the outcome is not one a run can have, or the line could not be
     *     appended
     */
    public static LedgerEntry recordFailure(
            Workspace workspace, String accession, String reason, FailureOptions options) {
        EntryOptions line = new EntryOptions(
                options.at(),
                options.profile(),
                OptionalInt.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.of(reason),
                List.of());
        return Ledger.appendEntry(workspace.paths().ledger(), Ledger.entryFor(accession, options.outcome(), line));
    }

    /**
     * What the workspace holds, read off the ledger rather than off the folders.
     *
     * @throws StoreError when the ledger is there and could not be read
     */
    public static ShiftReport shiftReport(Workspace workspace) {
        LedgerRead read = Ledger.readLedger(workspace.paths().ledger());
        return new ShiftReport(Ledger.summarise(read.entries()), read.unreadable());
    }
}
