package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** A file could not be read or written as the format it claims to be. */
public class CodecError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public CodecError(String message) {
        super(message, ErrorCode.CODEC);
    }

    /** A failure carrying a copy of the given details. */
    public CodecError(String message, Map<String, ?> details) {
        super(message, ErrorCode.CODEC, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public CodecError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.CODEC, details, cause);
    }
}
