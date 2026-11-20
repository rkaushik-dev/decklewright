package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/** What a crop is measured from. */
public enum Anchor implements JsonValue {
    /** The paper edge, for anything that is cropped to the whole sheet. */
    SHEET("sheet"),
    /** The printed border, for a map cropped to its neatline and a strip of collar. */
    NEATLINE("neatline");

    /** Every label, in declaration order. */
    static final List<String> LABELS =
            Arrays.stream(values()).map(Anchor::label).toList();

    private final String label;

    Anchor(String label) {
        this.label = label;
    }

    /**
     * The anchor a profile names.
     *
     * @throws BadArgumentError naming both anchors when the value is neither of them
     */
    public static Anchor parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "anchor"))];
    }

    /** The name a profile writes, such as {@code neatline}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how an anchor is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how an anchor reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
