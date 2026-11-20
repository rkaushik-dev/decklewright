package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * How the png writer filters its rows.
 *
 * @see Filters#chooseFilter(byte[], byte[], int)
 */
public enum FilterMode implements JsonValue {
    /** Each row gets whichever filter the heuristic scores best. */
    AUTO("auto"),
    /** Every row goes out unfiltered. */
    NONE("none");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(FilterMode::label).toList();

    private final String label;

    FilterMode(String label) {
        this.label = label;
    }

    /**
     * The mode a recipe names.
     *
     * @throws BadArgumentError naming every mode when the value is none of them
     */
    public static FilterMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "filter"))];
    }

    /** The name a recipe writes, such as {@code auto}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a mode is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a mode reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
