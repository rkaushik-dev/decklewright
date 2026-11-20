package io.github.rkaushikdev.decklewright;

/**
 * The stable codes a {@link DeckleError} can carry.
 *
 * <p>Operators read the code off a batch report and look it up in the runbook, so the
 * {@linkplain #value() printed values} are part of the contract and are never renamed once a
 * release has shipped with them.
 */
public enum ErrorCode {
    /** An internal invariant did not hold. The default when nothing more specific applies. */
    ASSERTION("DW_ASSERTION"),
    /** A value handed to a public method did not meet its stated contract. */
    BAD_ARGUMENT("DW_BAD_ARGUMENT"),
    /** A file could not be read or written as the format it claims to be. */
    CODEC("DW_CODEC"),
    /** The capture does not contain a findable object. */
    DETECTION("DW_DETECTION"),
    /** Geometry that cannot exist, such as a rect with negative extent. */
    GEOMETRY("DW_GEOMETRY"),
    /** A sidecar or a manifest is missing, unreadable, or from a later version. */
    MANIFEST("DW_MANIFEST"),
    /** A stage refused to run, or the graph itself is wrong. */
    PIPELINE("DW_PIPELINE"),
    /** No crop satisfies the rules the profile asked for. */
    PLAN("DW_PLAN"),
    /** A collection profile is malformed or contradicts itself. */
    PROFILE("DW_PROFILE"),
    /** A quality check could not be evaluated at all, as opposed to failing. */
    QC("DW_QC"),
    /** The workspace on disk is not in the shape the run expects. */
    STORE("DW_STORE"),
    /** Something real but not implemented for this input. */
    UNSUPPORTED("DW_UNSUPPORTED");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    /** The code as it is printed in reports and looked up in the runbook, such as {@code DW_CODEC}. */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
