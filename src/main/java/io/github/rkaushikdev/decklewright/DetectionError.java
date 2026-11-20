package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** The capture does not contain a findable object. */
public class DetectionError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public DetectionError(String message) {
        super(message, ErrorCode.DETECTION);
    }

    /** A failure carrying a copy of the given details. */
    public DetectionError(String message, Map<String, ?> details) {
        super(message, ErrorCode.DETECTION, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public DetectionError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.DETECTION, details, cause);
    }
}
