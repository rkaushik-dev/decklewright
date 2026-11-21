package io.github.rkaushikdev.decklewright.cli.commands;

import java.util.Arrays;
import java.util.Objects;

/**
 * A capture read off a disk for one command: the path as it was typed, the accession it names, and
 * its bytes.
 *
 * @param path the path, as typed
 * @param accession the file name without its extension
 * @param bytes the whole file, not copied; equality compares the content
 * @see Common#captureAt(String)
 */
public record CaptureFile(String path, String accession, byte[] bytes) {

    /** A capture of the given parts; the bytes are not copied. */
    public CaptureFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(accession, "accession");
        Objects.requireNonNull(bytes, "bytes");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CaptureFile that
                && path.equals(that.path)
                && accession.equals(that.accession)
                && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, accession, Arrays.hashCode(bytes));
    }

    @Override
    public String toString() {
        return "CaptureFile[path=" + path + ", accession=" + accession + ", bytes=" + bytes.length + "]";
    }
}
