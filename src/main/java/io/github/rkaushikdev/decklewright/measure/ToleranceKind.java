package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * How the two sides of a {@link Tolerance} are measured.
 *
 * @see Tolerance#boundsFor(double)
 */
public enum ToleranceKind implements JsonValue {
    /** The sides are in the same units as the target, such as half a degree of skew. */
    ABSOLUTE("absolute"),
    /** The sides are shares of the target's magnitude, such as one percent of the claimed ppi. */
    RELATIVE("relative");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(ToleranceKind::label).toList();

    private final String label;

    ToleranceKind(String label) {
        this.label = label;
    }

    /**
     * The kind a profile names.
     *
     * @throws BadArgumentError naming both kinds when the value is neither of them
     */
    public static ToleranceKind parse(String value) {
        return fromJson(value);
    }

    /**
     * The kind a json document names, which may be any value at all until it is checked.
     *
     * @throws BadArgumentError naming both kinds when the value is neither of them
     */
    static ToleranceKind fromJson(Object value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "kind"))];
    }

    /** The name a profile writes, such as {@code relative}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a kind is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a kind reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
