package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** No crop satisfies the rules the profile asked for. */
public class PlanError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public PlanError(String message) {
        super(message, ErrorCode.PLAN);
    }

    /** A failure carrying a copy of the given details. */
    public PlanError(String message, Map<String, ?> details) {
        super(message, ErrorCode.PLAN, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public PlanError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.PLAN, details, cause);
    }
}
