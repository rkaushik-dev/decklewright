package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * One of the four sides of a sheet or of the neatline drawn on it, in the order every reading
 * lists them: top, then clockwise.
 */
public enum Side implements JsonValue {
    /** The side nearest the top of the capture. */
    TOP("top"),
    /** The side nearest the right of the capture. */
    RIGHT("right"),
    /** The side nearest the bottom of the capture. */
    BOTTOM("bottom"),
    /** The side nearest the left of the capture. */
    LEFT("left");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Side::label).toList();

    private final String label;

    Side(String label) {
        this.label = label;
    }

    /**
     * The side a caller names, such as {@code top}.
     *
     * @throws BadArgumentError naming every side when the value is none of them
     */
    public static Side parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "side"))];
    }

    /** The name a reading prints, such as {@code top}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a side is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a side reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
