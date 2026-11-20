package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.util.JsonValue;

/**
 * What can be done with a finished job.
 *
 * <p>Held is not a third kind of failure. It means the machine will not decide and a person has to
 * look, and the material stays in working until they do.
 *
 * @see Policies#decide(DecisionInput, Policy)
 */
public enum Verdict implements JsonValue {
    /** It goes out, to delivered. */
    DELIVER("deliver"),
    /** It graded below the lab's level, and goes to rejected, where a person can overrule the grading. */
    REJECT("reject"),
    /** A person has to look before it goes anywhere. */
    HOLD("hold");

    private final String label;

    Verdict(String label) {
        this.label = label;
    }

    /** The word a log and the ledger use, such as {@code deliver}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a verdict is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a verdict reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
