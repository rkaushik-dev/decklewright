package io.github.rkaushikdev.decklewright.manifest;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What an event may carry beyond its kind, its agent and its sentence.
 *
 * @param values the figures behind the sentence, by name; empty for none. Copied, keeping their
 *     order
 * @param at when it happened; empty for now
 * @see Provenance#recordEvent(java.util.List, String, String, String, EventOptions)
 */
public record EventOptions(Optional<Map<String, Object>> values, Optional<Instant> at) {

    /** Options of the given parts; the values are copied. */
    public EventOptions {
        values = values.map(given -> Collections.unmodifiableMap(new LinkedHashMap<>(given)));
        Objects.requireNonNull(at, "at");
    }

    /** No values, and the time it is now. */
    public static EventOptions defaults() {
        return new EventOptions(Optional.empty(), Optional.empty());
    }

    /** These options with values; an empty map is recorded as an empty set of values. */
    public EventOptions withValues(Map<String, ?> given) {
        return new EventOptions(Optional.of(new LinkedHashMap<>(given)), at);
    }

    /** These options with a time. */
    public EventOptions withAt(Instant instant) {
        return new EventOptions(values, Optional.of(instant));
    }
}
