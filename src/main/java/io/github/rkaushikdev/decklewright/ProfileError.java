package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** A collection profile is malformed or contradicts itself. */
public class ProfileError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public ProfileError(String message) {
        super(message, ErrorCode.PROFILE);
    }

    /** A failure carrying a copy of the given details. */
    public ProfileError(String message, Map<String, ?> details) {
        super(message, ErrorCode.PROFILE, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public ProfileError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.PROFILE, details, cause);
    }
}
