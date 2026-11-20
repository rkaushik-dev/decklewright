package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * How a sample is read at a position that is not a pixel centre.
 *
 * @see Sampler#sample(double, double, int, SampleMode)
 */
public enum SampleMode implements JsonValue {
    /** The pixel whose centre is nearest, which never invents a value. */
    NEAREST("nearest"),
    /** A blend of the four pixels around the position. */
    BILINEAR("bilinear");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(SampleMode::label).toList();

    private final String label;

    SampleMode(String label) {
        this.label = label;
    }

    /**
     * The mode a caller or a profile names.
     *
     * @throws BadArgumentError naming every mode when the value is none of them
     */
    public static SampleMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "mode"))];
    }

    /** The name a profile writes, such as {@code bilinear}. */
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
