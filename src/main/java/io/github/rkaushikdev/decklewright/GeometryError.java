package io.github.rkaushikdev.decklewright;

import java.io.Serial;
import java.util.Map;

/** Geometry that cannot exist, such as a rect with negative extent. */
public class GeometryError extends DeckleError {
    @Serial
    private static final long serialVersionUID = 1L;

    /** A failure with no details. */
    public GeometryError(String message) {
        super(message, ErrorCode.GEOMETRY);
    }

    /** A failure carrying a copy of the given details. */
    public GeometryError(String message, Map<String, ?> details) {
        super(message, ErrorCode.GEOMETRY, details);
    }

    /** A failure carrying a copy of the given details and the foreign error that started it. */
    public GeometryError(String message, Map<String, ?> details, Throwable cause) {
        super(message, ErrorCode.GEOMETRY, details, cause);
    }
}
