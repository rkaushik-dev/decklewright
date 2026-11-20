package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.store.Folder;
import java.util.Objects;

/**
 * What the policy decided to do with a finished job, and why.
 *
 * @param decision what is done with it
 * @param reason in words, for the ledger and for a person, because the person who reads it will not
 *     have a code list
 * @param folder where the delivery goes: delivered, rejected, or working while it is held
 * @see Policies#decide(DecisionInput, Policy)
 */
public record Decision(Verdict decision, String reason, Folder folder) {

    /** A decision of the given parts. */
    public Decision {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(folder, "folder");
    }
}
