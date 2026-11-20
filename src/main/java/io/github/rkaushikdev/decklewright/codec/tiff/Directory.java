package io.github.rkaushikdev.decklewright.codec.tiff;

import java.util.List;

/**
 * One image file directory.
 *
 * @param entries every entry, in the order the file holds them
 * @param next where the next directory starts, or zero when this is the last
 */
public record Directory(List<Entry> entries, long next) {
    /** A directory of the given parts; the entries are copied. */
    public Directory {
        entries = List.copyOf(entries);
    }
}
