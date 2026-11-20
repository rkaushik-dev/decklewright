package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * Which way a mean profile collapses a region: one number per row, or one per column.
 *
 * @see Stats#meanProfile(Raster, Along, int)
 */
public enum Along implements JsonValue {
    /** One mean per row, taken across it. */
    ROW("row"),
    /** One mean per column, taken down it. */
    COLUMN("column");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Along::label).toList();

    private final String label;

    Along(String label) {
        this.label = label;
    }

    /**
     * The direction a caller or a profile names.
     *
     * @throws BadArgumentError naming every direction when the value is none of them
     */
    public static Along parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "along"))];
    }

    /** The name a profile writes, such as {@code column}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a direction is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a direction reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
