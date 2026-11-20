package io.github.rkaushikdev.decklewright.codec;

import java.util.Objects;
import java.util.Optional;

/**
 * How a netpbm file is written.
 *
 * @param ascii true for the plain text variants, for a fixture someone wants to diff
 * @param comment one line written into the header, when present
 */
public record PnmOptions(boolean ascii, Optional<String> comment) {
    /** Options of the given parts. */
    public PnmOptions {
        Objects.requireNonNull(comment, "comment");
    }

    /** Binary samples and no comment. */
    public static PnmOptions defaults() {
        return new PnmOptions(false, Optional.empty());
    }

    /** These options with the plain text variants on or off. */
    public PnmOptions withAscii(boolean ascii) {
        return new PnmOptions(ascii, comment);
    }

    /** These options with a header comment. */
    public PnmOptions withComment(String comment) {
        return new PnmOptions(ascii, Optional.of(comment));
    }
}
