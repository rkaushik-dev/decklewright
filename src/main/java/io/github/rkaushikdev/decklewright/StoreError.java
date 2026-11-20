package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** The workspace on disk is not in the shape the run expects. */
public class StoreError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public StoreError(String message) {
        super(message, ErrorCode.STORE);
    }

    /** A failure carrying a copy of the given details. */
    public StoreError(String message, Map<String, ?> details) {
        super(message, ErrorCode.STORE, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public StoreError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.STORE, details, cause);
    }
}
