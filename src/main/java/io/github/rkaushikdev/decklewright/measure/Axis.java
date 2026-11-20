package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * One of the two directions on the copy stand. A scanner does not have to sample both the same, so
 * a {@link Sampling} answers per axis.
 */
public enum Axis implements JsonValue {
    /** Across the bed, which is the width of a capture. */
    X("x"),
    /** Down the bed, which is the height of a capture. */
    Y("y");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Axis::label).toList();

    private final String label;

    Axis(String label) {
        this.label = label;
    }

    /**
     * The axis a caller names.
     *
     * @throws BadArgumentError naming both axes when the value is neither of them
     */
    public static Axis parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "axis"))];
    }

    /** The name a caller writes, such as {@code x}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how an axis is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how an axis reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
