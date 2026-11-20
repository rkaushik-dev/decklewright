package io.github.rkaushikdev.decklewright.manifest;

import java.util.Objects;

/**
 * One line read back from a sum file.
 *
 * @param digest the digest, lowercased whatever case it was written in
 * @param name the file name the line gives it for
 * @see Checksums#fromSumFile(String)
 */
public record SumLine(String digest, String name) {

    /** A line of the given parts. */
    public SumLine {
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(name, "name");
    }
}
