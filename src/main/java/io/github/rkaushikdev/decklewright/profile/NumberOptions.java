package io.github.rkaushikdev.decklewright.profile;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The bounds a {@linkplain Schema#number(NumberOptions) number field} enforces, with the same
 * demands every other field takes.
 *
 * @param min the lowest value allowed, inclusive; empty for no floor
 * @param max the highest value allowed, inclusive; empty for no ceiling
 * @param integer true when the value has to be a whole number
 * @param required true when a group missing this field is refused
 * @param fallback written data read in the field's place when it is missing; empty when a missing
 *     field is simply left out
 */
public record NumberOptions(
        OptionalDouble min, OptionalDouble max, boolean integer, boolean required, Optional<Object> fallback) {

    /** Options of the given bounds and demands. */
    public NumberOptions {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        Objects.requireNonNull(fallback, "fallback");
    }

    /** Any finite number, which may be left out and has no fallback. */
    public static NumberOptions defaults() {
        return new NumberOptions(OptionalDouble.empty(), OptionalDouble.empty(), false, false, Optional.empty());
    }

    /** These options with an inclusive floor. */
    public NumberOptions withMin(double min) {
        return new NumberOptions(OptionalDouble.of(min), max, integer, required, fallback);
    }

    /** These options with an inclusive ceiling. */
    public NumberOptions withMax(double max) {
        return new NumberOptions(min, OptionalDouble.of(max), integer, required, fallback);
    }

    /** These options, demanding a whole number or not. */
    public NumberOptions withInteger(boolean integer) {
        return new NumberOptions(min, max, integer, required, fallback);
    }

    /** These options, required or not. */
    public NumberOptions withRequired(boolean required) {
        return new NumberOptions(min, max, integer, required, fallback);
    }

    /** These options with the written data a missing field reads as. */
    public NumberOptions withFallback(Object fallback) {
        return new NumberOptions(min, max, integer, required, Optional.of(fallback));
    }

    /** The demands every field takes, without the bounds. */
    FieldOptions field() {
        return new FieldOptions(required, fallback);
    }
}
