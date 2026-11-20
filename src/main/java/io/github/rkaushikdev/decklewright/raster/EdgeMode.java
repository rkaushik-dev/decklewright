package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * What a read past the edge of a surface answers. The resampler and the rotation both reach past
 * the edge, and which answer is right depends on whether the result is a measurement or an output.
 *
 * @see Sampler#resolveIndex(int, int, EdgeMode)
 */
public enum EdgeMode implements JsonValue {
    /** The nearest edge sample, repeated outward. */
    CLAMP("clamp"),
    /** The samples reflected about the edge, without repeating the edge sample itself. */
    MIRROR("mirror"),
    /** Nothing: the reader falls back on its background. */
    CONSTANT("constant");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(EdgeMode::label).toList();

    private final String label;

    EdgeMode(String label) {
        this.label = label;
    }

    /**
     * The mode a caller or a profile names.
     *
     * @throws BadArgumentError naming every mode when the value is none of them
     */
    public static EdgeMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "edge"))];
    }

    /** The name a profile writes, such as {@code mirror}. */
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
