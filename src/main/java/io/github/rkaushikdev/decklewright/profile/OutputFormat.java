package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/** The formats a derivative may be written in. */
public enum OutputFormat implements JsonValue {
    /** Portable network graphics, which carries the sampling frequency and text metadata. */
    PNG("png"),
    /** Netpbm, which carries no metadata at all. */
    PNM("pnm");

    /** Every label, in declaration order. */
    static final List<String> LABELS =
            Arrays.stream(values()).map(OutputFormat::label).toList();

    private final String label;

    OutputFormat(String label) {
        this.label = label;
    }

    /**
     * The format a profile names.
     *
     * @throws BadArgumentError naming every format when the value is none of them
     */
    public static OutputFormat parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "format"))];
    }

    /** The name a profile writes, which is also the codec's name for it, such as {@code png}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a format is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a format reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
