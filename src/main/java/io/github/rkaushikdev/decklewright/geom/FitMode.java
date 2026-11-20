package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * How a service copy fills its target box. The crop has an aspect that rarely matches the box, and
 * which answer is right depends on the collection, so the profile picks one of these.
 *
 * @see Fitting#fitIn(Size, Rect, FitOptions)
 */
public enum FitMode implements JsonValue {
    /** Keeps the whole source visible and leaves margin. */
    CONTAIN("contain"),
    /** Fills the bound and lets the source overhang. */
    COVER("cover"),
    /** Ignores the aspect entirely. */
    STRETCH("stretch"),
    /** Leaves the source at its own size and only places it. */
    NONE("none");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(FitMode::label).toList();

    private final String label;

    FitMode(String label) {
        this.label = label;
    }

    /**
     * The mode a profile names.
     *
     * @throws BadArgumentError naming every mode when the value is none of them
     */
    public static FitMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "mode"))];
    }

    /** The name a profile writes, such as {@code contain}. */
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
