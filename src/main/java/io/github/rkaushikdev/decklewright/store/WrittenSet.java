package io.github.rkaushikdev.decklewright.store;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A set of files written into one directory together.
 *
 * @param directory where they were written
 * @param written every file's final path, in the order they were given; copied
 * @param bytes the size of every file together
 * @see AtomicFiles#writeSetAtomic(Path, List)
 */
public record WrittenSet(Path directory, List<Path> written, long bytes) {

    /** A written set of the given parts; the paths are copied. */
    public WrittenSet {
        Objects.requireNonNull(directory, "directory");
        written = List.copyOf(written);
    }
}
