package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * What a pad puts in the border it adds.
 *
 * @see Padding
 */
public enum PadMode implements JsonValue {
    /** A flat fill, because a made up edge would be a made up record. */
    CONSTANT("constant"),
    /** The nearest edge sample repeated, which a convolution running off the edge wants. */
    EDGE("edge"),
    /** The samples reflected about the edge, without repeating the edge sample itself. */
    MIRROR("mirror");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(PadMode::label).toList();

    private final String label;

    PadMode(String label) {
        this.label = label;
    }

    /**
     * The mode a caller or a profile names.
     *
     * @throws BadArgumentError naming every mode when the value is none of them
     */
    public static PadMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "mode"))];
    }

    /** The name a profile writes, such as {@code edge}. */
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
