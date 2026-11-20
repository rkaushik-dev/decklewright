package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * The bits per sample a derivative is written at.
 *
 * <p>A profile writes the depth as text, {@code "8"} or {@code "16"}, which is why this is a named
 * choice rather than a number; {@link #bits()} is the number the raster layer takes.
 */
public enum Depth implements JsonValue {
    /** Eight bits per sample, for service copies and thumbnails. */
    EIGHT("8", 8),
    /** Sixteen bits per sample, for masters. */
    SIXTEEN("16", 16);

    /** Every label, in declaration order. */
    static final List<String> LABELS = Arrays.stream(values()).map(Depth::label).toList();

    private final String label;
    private final int bits;

    Depth(String label, int bits) {
        this.label = label;
        this.bits = bits;
    }

    /**
     * The depth a profile names.
     *
     * @throws BadArgumentError naming both depths when the value is neither of them
     */
    public static Depth parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "depth"))];
    }

    /** The text a profile writes, such as {@code 16}. */
    public String label() {
        return label;
    }

    /** The bits per sample, 8 or 16. */
    public int bits() {
        return bits;
    }

    /** The {@linkplain #label() label}, which is how a depth is written in json: as text. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a depth reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
