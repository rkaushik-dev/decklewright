package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** A quality check could not be evaluated at all, as opposed to failing. */
public class QcError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public QcError(String message) {
        super(message, ErrorCode.QC);
    }

    /** A failure carrying a copy of the given details. */
    public QcError(String message, Map<String, ?> details) {
        super(message, ErrorCode.QC, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public QcError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.QC, details, cause);
    }
}
