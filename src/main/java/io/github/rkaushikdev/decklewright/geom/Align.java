package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * Where a box sits along one axis of the bound it is placed in.
 *
 * @see Fitting#alignIn(Size, Rect, Align, Align)
 */
public enum Align implements JsonValue {
    /** Against the left or top edge. */
    START("start"),
    /** Halfway along. */
    CENTRE("centre"),
    /** Against the right or bottom edge. */
    END("end");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Align::label).toList();

    private final String label;

    Align(String label) {
        this.label = label;
    }

    /**
     * The alignment a profile names.
     *
     * @param name which option this is, for the message: {@code alignX} or {@code alignY}
     * @throws BadArgumentError naming every alignment when the value is none of them
     */
    public static Align parse(String value, String name) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, name))];
    }

    /** The name a profile writes, such as {@code centre}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how an alignment is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how an alignment reads in a message. */
    @Override
    public String toString() {
        return label;
    }

    /** How far from the start of the span a box of the given extent sits. */
    double offset(double extent, double span) {
        return switch (this) {
            case START -> 0;
            case CENTRE -> (span - extent) / 2;
            case END -> span - extent;
        };
    }
}
