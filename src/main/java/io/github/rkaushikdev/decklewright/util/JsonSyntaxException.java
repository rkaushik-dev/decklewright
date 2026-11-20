package io.github.rkaushikdev.decklewright.util;

import java.io.Serial;

/**
 * Text handed to {@link Json#parse(String)} is not well formed json.
 *
 * <p>This is the reader's own complaint, not a domain error: the layer that was reading a sidecar,
 * a profile or a ledger line catches it and says which file it was.
 */
public final class JsonSyntaxException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int position;

    /**
     * @param message what was wrong
     * @param position the character offset the reader had reached
     */
    public JsonSyntaxException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }

    /** The character offset the reader had reached when it gave up. */
    public int position() {
        return position;
    }
}
