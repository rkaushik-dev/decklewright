package io.github.rkaushikdev.decklewright.store;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One file written into place.
 *
 * @param path where it was written, as given
 * @param bytes how large it is on disk now
 * @see AtomicFiles#writeAtomic(Path, byte[])
 */
public record WrittenFile(Path path, long bytes) {

    /** A written file of the given parts. */
    public WrittenFile {
        Objects.requireNonNull(path, "path");
    }
}
