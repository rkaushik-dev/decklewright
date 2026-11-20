package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** A value handed to a public method does not meet its stated contract. */
public class BadArgumentError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public BadArgumentError(String message) {
        super(message, ErrorCode.BAD_ARGUMENT);
    }

    /** A failure carrying a copy of the given details. */
    public BadArgumentError(String message, Map<String, ?> details) {
        super(message, ErrorCode.BAD_ARGUMENT, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public BadArgumentError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.BAD_ARGUMENT, details, cause);
    }
}
