package io.github.rkaushikdev.decklewright.store;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A capture waiting in the incoming folder.
 *
 * @param name the file name
 * @param path where it is
 * @param accession what it is of: the file name without its extension
 * @param bytes how large it is
 * @see Workspace#incoming()
 */
public record IncomingCapture(String name, Path path, String accession, long bytes) {

    /** A capture of the given parts. */
    public IncomingCapture {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(accession, "accession");
    }
}
