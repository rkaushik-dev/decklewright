package io.github.rkaushikdev.decklewright.profile;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether a {@link Field} has to be written, and what it reads as when it is not.
 *
 * @param required true when a group missing this field is refused
 * @param fallback written data read in the field's place when it is missing, such as {@code "0mm"};
 *     empty when a missing field is simply left out
 */
public record FieldOptions(boolean required, Optional<Object> fallback) {

    /** Options of the given demands. */
    public FieldOptions {
        Objects.requireNonNull(fallback, "fallback");
    }

    /** A field that may be left out and has no fallback. */
    public static FieldOptions defaults() {
        return new FieldOptions(false, Optional.empty());
    }

    /** These options, required or not. */
    public FieldOptions withRequired(boolean required) {
        return new FieldOptions(required, fallback);
    }

    /** These options with the written data a missing field reads as. */
    public FieldOptions withFallback(Object fallback) {
        return new FieldOptions(required, Optional.of(fallback));
    }
}
