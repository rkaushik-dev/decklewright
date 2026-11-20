package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** A stage refused to run, or the graph itself is wrong. */
public class PipelineError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public PipelineError(String message) {
        super(message, ErrorCode.PIPELINE);
    }

    /** A failure carrying a copy of the given details. */
    public PipelineError(String message, Map<String, ?> details) {
        super(message, ErrorCode.PIPELINE, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public PipelineError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.PIPELINE, details, cause);
    }
}
