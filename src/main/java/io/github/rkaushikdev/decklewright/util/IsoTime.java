package io.github.rkaushikdev.decklewright.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Timestamps as the lab's documents write them: UTC, always with milliseconds, such as {@code
 * 2025-12-18T12:00:00.000Z}.
 *
 * <p>{@link Instant#toString()} drops a zero fraction, which would change the bytes of every
 * sidecar and provenance file written on the second, so nothing in the tree uses it for output.
 */
public final class IsoTime {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private IsoTime() {}

    /** The instant in UTC with millisecond precision, truncating anything finer. */
    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }
}
