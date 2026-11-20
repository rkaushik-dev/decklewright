package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** Something real but not implemented for this input. */
public class UnsupportedError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public UnsupportedError(String message) {
        super(message, ErrorCode.UNSUPPORTED);
    }

    /** A failure carrying a copy of the given details. */
    public UnsupportedError(String message, Map<String, ?> details) {
        super(message, ErrorCode.UNSUPPORTED, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public UnsupportedError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.UNSUPPORTED, details, cause);
    }
}
