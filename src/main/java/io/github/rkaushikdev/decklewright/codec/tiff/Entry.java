package io.github.rkaushikdev.decklewright.codec.tiff;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One directory entry, with its value read.
 *
 * <p>The value is plain data, because the provenance record quotes tags back as they were found: a
 * {@code List<Double>} of the values for a numeric field, rationals already divided, or a {@code
 * String} for an ascii field.
 *
 * @param tag the tag number
 * @param name the tag's name, or {@code tag} and its number for one the reader does not know
 * @param type the field type code
 * @param count how many values the file says the field holds
 * @param value the values as a {@code List<Double>}, or the text as a {@code String}
 */
public record Entry(int tag, String name, int type, long count, Object value) {
    /**
     * An entry of the given parts; a list of values is copied, each as a {@link Double}.
     *
     * @throws BadArgumentError when the value is neither a list of numbers nor a string
     */
    public Entry {
        Objects.requireNonNull(name, "name");
        value = plain(value);
    }

    private static Object plain(Object value) {
        if (value instanceof String) {
            return value;
        }
        if (value instanceof List<?> items) {
            List<Double> numbers = new ArrayList<>(items.size());
            for (Object item : items) {
                if (!(item instanceof Number number)) {
                    throw notPlain(value);
                }
                numbers.add(number.doubleValue());
            }
            return Collections.unmodifiableList(numbers);
        }
        throw notPlain(value);
    }

    private static BadArgumentError notPlain(Object value) {
        return new BadArgumentError(
                "a tag value is a list of numbers or a string", Map.of("value", String.valueOf(value)));
    }
}
