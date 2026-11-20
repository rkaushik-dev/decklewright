package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** A sidecar or a manifest is missing, unreadable, or from a later version. */
public class ManifestError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public ManifestError(String message) {
        super(message, ErrorCode.MANIFEST);
    }

    /** A failure carrying a copy of the given details. */
    public ManifestError(String message, Map<String, ?> details) {
        super(message, ErrorCode.MANIFEST, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public ManifestError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.MANIFEST, details, cause);
    }
}
