package io.github.rkaushikdev.decklewright.store;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A delivery moved between folders.
 *
 * @param from where it was
 * @param to where it is now
 * @see Workspace#move(String, Folder, Folder)
 */
public record Moved(Path from, Path to) {

    /** A move of the given parts. */
    public Moved {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
